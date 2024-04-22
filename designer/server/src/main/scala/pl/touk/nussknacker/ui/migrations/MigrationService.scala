package pl.touk.nussknacker.ui.migrations

import cats.data.EitherT
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName, ProcessingType}
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioParameters
import pl.touk.nussknacker.restmodel.validation.ValidationResults
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationErrors
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.api.description.MigrationApiEndpoints.Dtos.MigrationError
import pl.touk.nussknacker.ui.api.{AuthorizeProcess, ListenerApiUser}
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent.OnSaved
import pl.touk.nussknacker.ui.listener.{ProcessChangeEvent, ProcessChangeListener, User}
import pl.touk.nussknacker.ui.migrations.MigrateScenarioData.CurrentMigrateScenarioData
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
import pl.touk.nussknacker.ui.util.{ApiAdapterServiceError, OutOfRangeAdapterRequestError}
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
      migrateScenarioData: MigrateScenarioData
  )(implicit loggedUser: LoggedUser): EitherT[Future, MigrationError, Unit] = {

    val localScenarioDescriptionVersion  = migrationApiAdapterService.getCurrentApiVersion
    val remoteScenarioDescriptionVersion = migrateScenarioData.currentVersion()
    val versionsDifference               = localScenarioDescriptionVersion - remoteScenarioDescriptionVersion

    val liftedMigrateScenarioRequestE: Either[ApiAdapterServiceError, MigrateScenarioData] =
      if (versionsDifference > 0) {
        migrationApiAdapterService.adaptUp(migrateScenarioData, versionsDifference)
      } else Right(migrateScenarioData)

    liftedMigrateScenarioRequestE match {
      case Left(apiAdapterServiceError) =>
        throw new IllegalStateException(apiAdapterServiceError match {
          case OutOfRangeAdapterRequestError(currentVersion, signedNoOfVersionsLeftToApply) =>
            signedNoOfVersionsLeftToApply match {
              case n if n >= 0 =>
                s"Migration API Adapter error occurred when trying to adapt MigrateScenarioRequest in version: $currentVersion to $signedNoOfVersionsLeftToApply version(s) up"
              case _ =>
                s"Migration API Adapter error occurred when trying to adapt MigrateScenarioRequest in version: $currentVersion to ${-signedNoOfVersionsLeftToApply} version(s) down"
            }
        })
      case Right(currentMigrateScenarioRequest: CurrentMigrateScenarioData) =>
        EitherT(migrateCurrentScenarioDescription(currentMigrateScenarioRequest))
      case _ =>
        throw new IllegalStateException(
          "Migration API adapter service lifted up remote migration request not to its newest local version"
        )
    }
  }

  // FIXME: Rename process to scenario everywhere it's possible
  private def migrateCurrentScenarioDescription(
      migrateScenarioData: CurrentMigrateScenarioData
  )(implicit loggedUser: LoggedUser): Future[Either[MigrationError, Unit]] = {
    val sourceEnvironmentId = migrateScenarioData.sourceEnvironmentId
    val targetEnvironmentId = config.getString("environment")
    val parameters = ScenarioParameters(
      migrateScenarioData.processingMode,
      migrateScenarioData.processCategory,
      migrateScenarioData.engineSetupName
    )
    val scenarioGraph = migrateScenarioData.scenarioGraph
    val processName   = migrateScenarioData.processName
    val isFragment    = migrateScenarioData.isFragment
    val forwardedUsernameO =
      if (passUsernameInMigration) Some(RemoteUserName(migrateScenarioData.remoteUserName)) else None
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

    result.leftMap {
      case _: UnauthorizedError => MigrationError.InsufficientPermission(loggedUser)
      case _ @MigrationToArchivedError(processName, environment) =>
        MigrationError.CannotMigrateArchivedScenario(processName, environment)
      case _ @MigrationValidationError(errors) => MigrationError.InvalidScenario(errors)
      case nuDesignerError: NuDesignerError    => throw new IllegalStateException(nuDesignerError.getMessage)
    }.value
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
    EitherT.fromEither[Future](
      processResolver
        .forProcessingTypeEUnsafe(processingType) match {
        case Left(e) => Left(e)
        case Right(uiProcessResolver) =>
          FatalValidationError.renderNotAllowedAsError(
            uiProcessResolver.validateBeforeUiResolving(scenarioGraph, processName, isFragment)
          )
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
