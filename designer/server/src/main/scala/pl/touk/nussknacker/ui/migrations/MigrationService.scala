package pl.touk.nussknacker.ui.migrations

import akka.http.scaladsl.model.StatusCode
import cats.data.EitherT
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName, ProcessingType}
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioParameters
import pl.touk.nussknacker.restmodel.validation.ValidationResults
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationErrors
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.api.description.MigrationApiEndpoints.Dtos.MigrateScenarioRequestDto
import pl.touk.nussknacker.ui.api.{AuthorizeProcess, ListenerApiUser}
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent.OnSaved
import pl.touk.nussknacker.ui.listener.{ProcessChangeEvent, ProcessChangeListener, User}
import pl.touk.nussknacker.ui.migrations.MigrateScenarioData.CurrentMigrateScenarioRequest
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.ProcessService.{
  CreateScenarioCommand,
  FetchScenarioGraph,
  GetScenarioWithDetailsOptions,
  UpdateScenarioCommand
}
import pl.touk.nussknacker.ui.process.migrate.{
  MigrationApiAdapterError,
  MigrationToArchivedError,
  MigrationValidationError,
  RemoteEnvironmentCommunicationError
}
import pl.touk.nussknacker.ui.process.processingtype.{ProcessingTypeDataProvider, ScenarioParametersService}
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.RemoteUserName
import pl.touk.nussknacker.ui.process.repository.UpdateProcessComment
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver
import pl.touk.nussknacker.ui.util.ApiAdapterServiceError
import pl.touk.nussknacker.ui.validation.FatalValidationError
import pl.touk.nussknacker.ui.{NuDesignerError, UnauthorizedError}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class MigrationService(
    config: Config,
    processService: ProcessService,
    processResolver: ProcessingTypeDataProvider[UIProcessResolver, _],
    processAuthorizer: AuthorizeProcess,
    processChangeListener: ProcessChangeListener,
    scenarioParametersService: ProcessingTypeDataProvider[_, ScenarioParametersService],
    useLegacyCreateScenarioApi: Boolean,
    migrationApiAdapterService: MigrationApiAdapterService
)(implicit val ec: ExecutionContext) {

  private val passUsernameInMigration = true

  def migrate(
      req: MigrateScenarioRequestDto
  )(implicit loggedUser: LoggedUser): EitherT[Future, NuDesignerError, Unit] = {

    val migrateScenarioRequest = MigrateScenarioData.toDomain(req)

    val localScenarioDescriptionVersion  = migrationApiAdapterService.getCurrentApiVersion
    val remoteScenarioDescriptionVersion = migrateScenarioRequest.currentVersion()
    val versionsDifference               = localScenarioDescriptionVersion - remoteScenarioDescriptionVersion

    val liftedMigrateScenarioRequestE: Either[ApiAdapterServiceError, MigrateScenarioData] =
      if (versionsDifference > 0) {
        migrationApiAdapterService.adaptUp(migrateScenarioRequest, versionsDifference)
      } else Right(migrateScenarioRequest)

    liftedMigrateScenarioRequestE match {
      case Left(apiAdapterServiceError) =>
        EitherT(
          Future[Either[NuDesignerError, Unit]](
            Left(
              MigrationApiAdapterError(apiAdapterServiceError)
            )
          )
        )
      case Right(liftedMigrateScenarioRequest) =>
        liftedMigrateScenarioRequest match {
          case currentMigrateScenarioRequest: CurrentMigrateScenarioRequest =>
            EitherT(migrateCurrentScenarioDescription(currentMigrateScenarioRequest))
          case _ =>
            EitherT(
              Future[Either[NuDesignerError, Unit]](
                Left(
                  RemoteEnvironmentCommunicationError(
                    StatusCode.int2StatusCode(500),
                    "Migration API adapter service lifted up remote migration request not to its newest local version"
                  )
                )
              )
            )
        }
    }
  }

  // FIXME: Rename process to scenario everywhere it's possible
  private def migrateCurrentScenarioDescription(
      migrateScenarioRequest: CurrentMigrateScenarioRequest
  )(implicit loggedUser: LoggedUser): Future[Either[NuDesignerError, Unit]] = {
    val sourceEnvironmentId = migrateScenarioRequest.sourceEnvironmentId
    val targetEnvironmentId = config.getString("environment")
    val parameters = ScenarioParameters(
      migrateScenarioRequest.processingMode,
      migrateScenarioRequest.processCategory,
      migrateScenarioRequest.engineSetupName
    )
    val scenarioGraph = migrateScenarioRequest.scenarioGraph
    val processName   = migrateScenarioRequest.processName
    val isFragment    = migrateScenarioRequest.isFragment
    val forwardedUsernameO =
      if (passUsernameInMigration) Some(RemoteUserName(migrateScenarioRequest.remoteUserName)) else None
    val updateProcessComment = {
      forwardedUsernameO match {
        case Some(forwardedUsername) =>
          UpdateProcessComment(s"Scenario migrated from $sourceEnvironmentId by ${forwardedUsername.name}")
        case None =>
          UpdateProcessComment(s"Scenario migrated from $sourceEnvironmentId by Unknown user")
      }
    }
    val updateScenarioCommand =
      UpdateScenarioCommand(scenarioGraph, updateProcessComment, forwardedUsernameO)

    val processingTypeValidated = scenarioParametersService.combined.queryProcessingTypeWithWritePermission(
      Some(parameters.category),
      Some(parameters.processingMode),
      Some(parameters.engineSetupName)
    )

    val result: EitherT[Future, NuDesignerError, Unit] = for {
      processingType <- EitherT.fromEither[Future](processingTypeValidated.toEither)
      validationResult <-
        validateProcessingTypeAndUIProcessResolver(scenarioGraph, processName, isFragment, processingType)
      _ <- checkForValidationErrors(validationResult)
      _ <- checkOrCreateAndCheckArchivedProcess(
        processName,
        targetEnvironmentId,
        parameters,
        isFragment,
        forwardedUsernameO
      )
      processId <- getProcessId(processName)
      processIdWithName = ProcessIdWithName(processId, processName)
      _ <- checkLoggedUserCanWriteToProcess(processId)
      _ <- checkLoggedUserCanOverrideProcess(processId, forwardedUsernameO)
      _ <- updateProcessAndNotifyListeners(updateScenarioCommand, processIdWithName)
    } yield ()

    result.value
  }

  private def checkLoggedUserCanWriteToProcess(processId: ProcessId)(implicit loggedUser: LoggedUser) = {
    EitherT
      .liftF(processAuthorizer.check(processId, Permission.Write, loggedUser))
      .subflatMap {
        case true  => Right(())
        case false => Left(new UnauthorizedError(loggedUser))
      }
  }

  private def updateProcessAndNotifyListeners(
      updateScenarioCommand: UpdateScenarioCommand,
      processIdWithName: ProcessIdWithName
  )(implicit loggedUser: LoggedUser) = {
    EitherT
      .liftF[Future, NuDesignerError, ValidationResults.ValidationResult](
        processService
          .updateProcess(processIdWithName, updateScenarioCommand)
          .withSideEffect(response =>
            response.processResponse.foreach(resp => notifyListener(OnSaved(resp.id, resp.versionId)))
          )
          .map(_.validationResult)
      )
  }

  private def checkLoggedUserCanOverrideProcess(processId: ProcessId, forwardedUsername: Option[RemoteUserName])(
      implicit loggedUser: LoggedUser
  ) = {
    EitherT
      .liftF[Future, NuDesignerError, Boolean](
        processAuthorizer
          .check(processId, Permission.OverrideUsername, loggedUser)
          .map(_ || forwardedUsername.isEmpty)
      )
      .subflatMap {
        case true  => Right(())
        case false => Left(new UnauthorizedError(loggedUser))
      }
  }

  private def getProcessId(processName: ProcessName) = {
    EitherT.liftF[Future, NuDesignerError, ProcessId](
      processService.getProcessIdUnsafe(processName)
    )
  }

  private def checkOrCreateAndCheckArchivedProcess(
      processName: ProcessName,
      targetEnvironmentId: String,
      parameters: ScenarioParameters,
      isFragment: Boolean,
      forwardedUsername: Option[RemoteUserName]
  )(implicit loggedUser: LoggedUser) = {
    EitherT[Future, NuDesignerError, Unit](
      processService.getProcessId(processName).flatMap[Either[NuDesignerError, Unit]] {
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
            .transformWith[Either[NuDesignerError, Unit]] {
              case Success(scenarioWithDetails) if scenarioWithDetails.isArchived =>
                Future
                  .successful(Left(MigrationToArchivedError(scenarioWithDetails.name, targetEnvironmentId)))
              case Success(_)                  => Future.successful(Right(()))
              case Failure(e: NuDesignerError) => Future.successful(Left(e))
              case Failure(e)                  => Future.failed(e)
            }

        case None =>
          createProcess(processName, parameters, isFragment, forwardedUsername, useLegacyCreateScenarioApi)
      }
    )
  }

  private def checkForValidationErrors(validationResult: ValidationResults.ValidationResult) = {
    EitherT.cond[Future](
      validationResult.errors == ValidationErrors.success,
      (),
      MigrationValidationError(validationResult.errors)
    )
  }

  private def validateProcessingTypeAndUIProcessResolver(
      scenarioGraph: ScenarioGraph,
      processName: ProcessName,
      isFragment: Boolean,
      processingType: ProcessingType
  )(implicit loggedUser: LoggedUser) = {
    EitherT[Future, NuDesignerError, ValidationResults.ValidationResult](
      processResolver
        .forProcessingTypeE(processingType) match {
        case Left(e) => Future.successful[Either[NuDesignerError, ValidationResults.ValidationResult]](Left(e))
        case Right(uiProcessResolverO) =>
          uiProcessResolverO match {
            case Some(uiProcessResolver) =>
              Future.successful[Either[NuDesignerError, ValidationResults.ValidationResult]](
                FatalValidationError.renderNotAllowedAsError(
                  uiProcessResolver.validateBeforeUiResolving(scenarioGraph, processName, isFragment)
                )
              )
            case None =>
              Future.failed(
                new IllegalStateException(
                  s"Error while providing process resolver for processing type $processingType requested by user ${loggedUser.username}"
                )
              )
          }
      }
    )
  }

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
