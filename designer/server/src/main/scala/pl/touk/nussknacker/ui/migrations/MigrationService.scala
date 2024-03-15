package pl.touk.nussknacker.ui.migrations

import cats.data.Validated
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName, ProcessingType}
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioParameters
import pl.touk.nussknacker.restmodel.validation.ValidationResults
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationErrors
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.api.MigrationApiEndpoints.Dtos.MigrateScenarioRequestV2
import pl.touk.nussknacker.ui.api.{AuthorizeProcess, ListenerApiUser}
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent.OnSaved
import pl.touk.nussknacker.ui.listener.{ProcessChangeEvent, ProcessChangeListener, User}
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.ProcessService.{
  CreateScenarioCommand,
  FetchScenarioGraph,
  GetScenarioWithDetailsOptions,
  UpdateScenarioCommand
}
import pl.touk.nussknacker.ui.process.migrate.{MigrationToArchivedError, MigrationValidationError}
import pl.touk.nussknacker.ui.process.processingtype.{ProcessingTypeDataProvider, ScenarioParametersService}
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.RemoteUserName
import pl.touk.nussknacker.ui.process.repository.UpdateProcessComment
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver
import pl.touk.nussknacker.ui.validation.FatalValidationError
import pl.touk.nussknacker.ui.{MissingProcessResolverError, NuDesignerError, UnauthorizedError}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class MigrationService(
    config: Config,
    processService: ProcessService,
    processResolver: ProcessingTypeDataProvider[UIProcessResolver, _],
    processAuthorizer: AuthorizeProcess,
    processChangeListener: ProcessChangeListener,
    scenarioParametersService: ProcessingTypeDataProvider[_, ScenarioParametersService],
    useLegacyCreateScenarioApi: Boolean
)(implicit val ec: ExecutionContext) {

  private val passUsernameInMigration = true

  def migrate(
      migrateScenarioRequest: MigrateScenarioRequestV2
  )(implicit loggedUser: LoggedUser): Future[Either[NuDesignerError, Unit]] = {
    val sourceEnvironmentId = migrateScenarioRequest.sourceEnvironmentId
    val targetEnvironmentId = config.getString("environment")
    val parameters = ScenarioParameters(
      migrateScenarioRequest.processingMode,
      migrateScenarioRequest.processCategory,
      migrateScenarioRequest.engineSetupName
    )
    val scenarioGraph     = migrateScenarioRequest.scenarioGraph
    val processName       = migrateScenarioRequest.processName
    val isFragment        = migrateScenarioRequest.isFragment
    val forwardedUser     = if (passUsernameInMigration) Some(loggedUser) else None
    val forwardedUsername = forwardedUser.map(user => RemoteUserName(user.username))
    val updateProcessComment =
      UpdateProcessComment(s"Scenario migrated from $sourceEnvironmentId by ${loggedUser.username}")
    val updateScenarioCommand =
      UpdateScenarioCommand(scenarioGraph, updateProcessComment, forwardedUsername)

    val processingTypeValidated = scenarioParametersService.combined.queryProcessingTypeWithWritePermission(
      Some(parameters.category),
      Some(parameters.processingMode),
      Some(parameters.engineSetupName)
    )

    val future: Future[Unit] = for {

      processingType <- resolveValidatedProcessingType(processingTypeValidated)

      validation <-
        processResolver
          .forTypeE(processingType) match {
          case Left(e) => Future.successful[Either[NuDesignerError, ValidationResults.ValidationResult]](Left(e))
          case Right(uiProcessResolverO) =>
            uiProcessResolverO match {
              case Some(uiProcessResolver) =>
                Future.successful(
                  FatalValidationError.renderNotAllowedAsError(
                    uiProcessResolver.validateBeforeUiResolving(scenarioGraph, processName, isFragment)
                  )
                )
              case None => Future.successful(Left(new MissingProcessResolverError(loggedUser, processingType)))
            }

        }

      _ <- validation match {
        case Left(e) => Future.failed(e)
        case Right(validationResult) =>
          if (validationResult.errors != ValidationErrors.success)
            Future.failed(MigrationValidationError(validationResult.errors))
          else Future.successful(())
      }

      processIdO <- processService.getProcessId(processName)

      _ <- processIdO match {
        case Some(pid) =>
          val processIdWithName = ProcessIdWithName(pid, processName)
          processService
            .getLatestProcessWithDetails(
              processIdWithName,
              GetScenarioWithDetailsOptions(
                FetchScenarioGraph(FetchScenarioGraph.DontValidate),
                fetchState = true
              )
            )
            .transformWith[Unit] {
              case Success(scenarioWithDetails) if scenarioWithDetails.isArchived =>
                Future
                  .failed(MigrationToArchivedError(scenarioWithDetails.name, targetEnvironmentId))
              case Success(_) => Future.successful(())
              case Failure(e) => Future.failed(e)
            }
        case None =>
          createProcess(processName, parameters, isFragment, forwardedUsername, useLegacyCreateScenarioApi)
      }

      processId <- processService.getProcessIdUnsafe(processName)
      processIdWithName = ProcessIdWithName(processId, processName)

      canWrite <- processAuthorizer.check(processId, Permission.Write, loggedUser)
      _        <- if (canWrite) Future.successful(()) else Future.failed(new UnauthorizedError(loggedUser))

      canOverrideUsername <- processAuthorizer
        .check(processId, Permission.OverrideUsername, loggedUser)
        .map(_ || forwardedUsername.isEmpty)
      _ <-
        if (canOverrideUsername) Future.successful(Right(()))
        else Future.failed(new UnauthorizedError(loggedUser))

      _ <- processService
        .updateProcess(processIdWithName, updateScenarioCommand)
        .withSideEffect(response =>
          response.processResponse.foreach(resp => notifyListener(OnSaved(resp.id, resp.versionId)))
        )
        .map(_.validationResult)

    } yield ()

    val transformedFuture: Future[Either[NuDesignerError, Unit]] =
      future.transform[Either[NuDesignerError, Unit]] { (t: Try[Unit]) =>
        t match {
          case Failure(e: NuDesignerError) => Success(Left(e))
          case Failure(e: Throwable)       => Failure(e)
          case Success(())                 => Success(Right(()))
        }
      }

    transformedFuture
  }

  private def resolveValidatedProcessingType(
      validatedProcessingType: Validated[NuDesignerError, ProcessingType]
  ): Future[ProcessingType] =
    validatedProcessingType.fold(Future.failed, Future.successful)

  private def notifyListener(event: ProcessChangeEvent)(implicit user: LoggedUser): Unit = {

    implicit val listenerUser: User = ListenerApiUser(user)
    processChangeListener.handle(event)
  }

  private def createProcess(
      processName: ProcessName,
      parameters: ScenarioParameters,
      isFragment: Boolean,
      forwardedUsername: Option[RemoteUserName],
      useLegacyCreateScenarioApi: Boolean
  )(implicit loggedUser: LoggedUser): Future[Either[NuDesignerError, Unit]] = if (useLegacyCreateScenarioApi) {
    processService
      .createProcess(
        CreateScenarioCommand(processName, Some(parameters.category), None, None, isFragment, forwardedUsername)
      )
      .map(_.toEither)
      .map {
        case Left(value) => Left(value)
        case Right(response) =>
          notifyListener(OnSaved(response.id, response.versionId))
          Right(())
      }
  } else {
    val createScenarioCommand = CreateScenarioCommand(
      processName,
      Some(parameters.category),
      Some(parameters.processingMode),
      Some(parameters.engineSetupName),
      isFragment = isFragment,
      forwardedUserName = forwardedUsername
    )
    processService
      .createProcess(createScenarioCommand)
      .map(_.toEither)
      .map {
        case Left(value) => Left(value)
        case Right(response) =>
          notifyListener(OnSaved(response.id, response.versionId))
          Right(())
      }
  }

}
