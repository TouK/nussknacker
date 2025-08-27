package pl.touk.nussknacker.ui.migrations

import cats.data.EitherT
import cats.implicits._
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError.IncompatibleParameterDefinitionErrorDetails
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcessConverter
import pl.touk.nussknacker.engine.dict.DictKeyParameterAdapter
import pl.touk.nussknacker.engine.dict.DictKeyParameterAdapter.ParameterToAdapt
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioParameters
import pl.touk.nussknacker.restmodel.validation.ValidationResults
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{ValidationErrors, ValidationResult}
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.{FatalError, NuDesignerError, UnauthorizedError}
import pl.touk.nussknacker.ui.api.{AuthorizeProcess, ListenerApiUser}
import pl.touk.nussknacker.ui.api.description.MigrationApiEndpoints.MigrationError
import pl.touk.nussknacker.ui.config.DesignerConfig
import pl.touk.nussknacker.ui.listener.{ProcessChangeEvent, ProcessChangeListener, User}
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent.OnSaved
import pl.touk.nussknacker.ui.migrations.MigrateScenarioData.CurrentMigrateScenarioData
import pl.touk.nussknacker.ui.migrations.MigrationService.{toMigrationError, MigrationApiAdapterError}
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.ProcessService.{
  CreateScenarioCommand,
  FetchScenarioGraph,
  GetScenarioWithDetailsOptions,
  MigrateScenarioCommand
}
import pl.touk.nussknacker.ui.process.label.ScenarioLabel
import pl.touk.nussknacker.ui.process.processingtype.ScenarioParametersService
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver
import pl.touk.nussknacker.ui.util.{ApiAdapterServiceError, OutOfRangeAdapterRequestError}
import pl.touk.nussknacker.ui.validation.FatalValidationError

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class MigrationService(
    designerConfig: DesignerConfig,
    processService: ProcessService,
    processResolver: ProcessingTypeDataProvider[UIProcessResolver, _],
    processAuthorizer: AuthorizeProcess,
    processChangeListener: ProcessChangeListener,
    scenarioParametersService: ProcessingTypeDataProvider[_, ScenarioParametersService],
    useLegacyCreateScenarioApi: Boolean,
    migrationApiAdapterService: MigrationApiAdapterService,
)(implicit val ec: ExecutionContext) {

  def migrate(
      migrateScenarioData: MigrateScenarioData
  )(implicit loggedUser: LoggedUser): Future[Either[MigrationError, Unit]] = {

    val localScenarioDescriptionVersion  = migrationApiAdapterService.getCurrentApiVersion
    val remoteScenarioDescriptionVersion = migrateScenarioData.currentVersion
    val versionsDifference               = localScenarioDescriptionVersion - remoteScenarioDescriptionVersion

    val liftedMigrateScenarioRequestE: Either[ApiAdapterServiceError, MigrateScenarioData] =
      if (versionsDifference > 0) {
        migrationApiAdapterService.adaptUp(migrateScenarioData, versionsDifference)
      } else Right(migrateScenarioData)

    liftedMigrateScenarioRequestE match {
      case Left(apiAdapterServiceError) =>
        throw MigrationApiAdapterError(apiAdapterServiceError)
      case Right(currentMigrateScenarioRequest: CurrentMigrateScenarioData) =>
        migrateCurrentScenarioDescription(currentMigrateScenarioRequest)
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
    val targetEnvironmentId = designerConfig.environment
    val parameters = ScenarioParameters(
      migrateScenarioData.processingMode,
      migrateScenarioData.processCategory,
      migrateScenarioData.engineSetupName
    )
    val scenarioGraph  = migrateScenarioData.scenarioGraph
    val processName    = migrateScenarioData.processName
    val isFragment     = migrateScenarioData.isFragment
    val scenarioLabels = migrateScenarioData.scenarioLabels.map(ScenarioLabel.apply)

    val processingTypeValidated = scenarioParametersService.combined.queryProcessingTypeWithWritePermission(
      Some(parameters.category),
      Some(parameters.processingMode),
      Some(parameters.engineSetupName)
    )

    val result: EitherT[Future, MigrationError, Unit] = for {
      processingType <- EitherT.fromEither[Future](processingTypeValidated.toEither).leftMap(toMigrationError)
      validationResult <-
        validateProcessingTypeAndUIProcessResolver(
          scenarioGraph,
          processName,
          isFragment,
          scenarioLabels,
          processingType
        )
      processVersion = ProcessVersion(
        versionId = migrateScenarioData.sourceScenarioVersionId.getOrElse(VersionId.initialVersionId),
        processName = processName,
        processId = ProcessId(1),
        labels = migrateScenarioData.scenarioLabels,
        user = loggedUser.username,
        modelVersion = None
      )
      scenarioGraphAfterAdaptations = adaptDictionaryParameters(
        scenarioGraph,
        processVersion,
        processingType,
        isFragment,
        validationResult,
      )
      validationResultAfterAdaptations <-
        validateProcessingTypeAndUIProcessResolver(
          scenarioGraphAfterAdaptations,
          processName,
          isFragment,
          scenarioLabels,
          processingType
        )
      _ <- checkForValidationErrors(validationResultAfterAdaptations)
      _ <- checkOrCreateAndCheckArchivedProcess(
        processName,
        targetEnvironmentId,
        parameters,
        isFragment,
      )
      processId <- getProcessId(processName)
      processIdWithName = ProcessIdWithName(processId, processName)
      _ <- checkLoggedUserCanWriteToProcess(processId)
      migrateScenarioCommand =
        MigrateScenarioCommand(
          scenarioGraph = scenarioGraphAfterAdaptations,
          scenarioLabels = Some(scenarioLabels.map(_.value)),
          sourceEnvironment = sourceEnvironmentId,
          targetEnvironment = targetEnvironmentId,
          sourceScenarioVersionId = migrateScenarioData.sourceScenarioVersionId,
        )
      _ <- migrateProcessAndNotifyListeners(migrateScenarioCommand, processIdWithName)
    } yield ()

    result.value
  }

  private def adaptDictionaryParameters(
      scenarioGraph: ScenarioGraph,
      processVersion: ProcessVersion,
      processingType: ProcessingType,
      isFragment: Boolean,
      validationResult: ValidationResult,
  )(implicit loggedUser: LoggedUser): ScenarioGraph = {
    val dictionaryParametersToAdapt = validationResult.errors.invalidNodes.values
      .flatMap(_.flatMap(_.details))
      .collect { case details: IncompatibleParameterDefinitionErrorDetails => details }
      .map(error => ParameterToAdapt(error.nodeId, error.paramName, error.parameterEditors))
      .toList
      .toNel

    dictionaryParametersToAdapt match {
      case Some(parametersToAdapt) =>
        val process         = CanonicalProcessConverter.fromScenarioGraph(scenarioGraph, processVersion.processName)
        val modifiedProcess = DictKeyParameterAdapter.adaptDictKeyParameters(process, parametersToAdapt.toList)
        val resolver        = processResolver.forProcessingTypeUnsafe(processingType)
        val modifiedScenarioGraph = resolver
          .validateAndReverseResolve(
            canonical = modifiedProcess,
            processVersion = processVersion,
            isFragment = isFragment,
            removeDictLabels = false,
          )
          .scenarioGraph
        modifiedScenarioGraph
      case None =>
        scenarioGraph
    }
  }

  private def checkLoggedUserCanWriteToProcess(processId: ProcessId)(implicit loggedUser: LoggedUser) = {
    EitherT
      .liftF(processAuthorizer.check(processId, Permission.Write, loggedUser))
      .subflatMap {
        case true  => Right(())
        case false => Left(new UnauthorizedError(loggedUser))
      }
      .leftMap(toMigrationError)
  }

  private def migrateProcessAndNotifyListeners(
      migrateScenarioCommand: MigrateScenarioCommand,
      processIdWithName: ProcessIdWithName
  )(implicit loggedUser: LoggedUser) = {
    EitherT
      .liftF[Future, NuDesignerError, ValidationResults.ValidationResult](
        processService
          .migrateProcess(processIdWithName, migrateScenarioCommand)
          .withSideEffect(response =>
            response.processResponse.foreach(resp => notifyListener(OnSaved(resp.id, resp.versionId)))
          )
          .map(_.validationResult)
      )
      .leftMap(toMigrationError)
  }

  private def getProcessId(processName: ProcessName): EitherT[Future, MigrationError, ProcessId] = {
    EitherT.liftF[Future, MigrationError, ProcessId](
      processService.getProcessIdUnsafe(processName)
    )
  }

  private def checkOrCreateAndCheckArchivedProcess(
      processName: ProcessName,
      targetEnvironmentId: String,
      parameters: ScenarioParameters,
      isFragment: Boolean,
  )(implicit loggedUser: LoggedUser): EitherT[Future, MigrationError, Unit] = {
    EitherT(
      processService.getProcessId(processName).flatMap[Either[MigrationError, Unit]] {
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
            .transformWith[Either[MigrationError, Unit]] {
              case Success(scenarioWithDetails) if scenarioWithDetails.isArchived =>
                Future.successful(
                  Left(MigrationError.CannotMigrateArchivedScenario(scenarioWithDetails.name, targetEnvironmentId))
                )
              case Success(_)                  => Future.successful(Right(()))
              case Failure(e: NuDesignerError) => Future.successful(Left(toMigrationError(e)))
              case Failure(e)                  => Future.failed(e)
            }
        case None =>
          createProcess(processName, parameters, isFragment, useLegacyCreateScenarioApi)
            .map(_.left.map(toMigrationError))

      }
    )
  }

  private def checkForValidationErrors(
      validationResult: ValidationResults.ValidationResult
  ): EitherT[Future, MigrationError, Unit] = {
    EitherT
      .cond[Future](
        validationResult.errors == ValidationErrors.success,
        (),
        MigrationError.InvalidScenario(validationResult.errors)
      )
  }

  private def validateProcessingTypeAndUIProcessResolver(
      scenarioGraph: ScenarioGraph,
      processName: ProcessName,
      isFragment: Boolean,
      labels: List[ScenarioLabel],
      processingType: ProcessingType
  )(implicit loggedUser: LoggedUser) = {
    EitherT
      .fromEither[Future](
        processResolver
          .forProcessingTypeEUnsafe(processingType) match {
          case Left(e) => Left(e)
          case Right(uiProcessResolver) =>
            FatalValidationError.renderNotAllowedAsError(
              uiProcessResolver.validateBeforeUiResolving(scenarioGraph, processName, isFragment, labels)
            )
        }
      )
      .leftMap(toMigrationError)
  }

  private def notifyListener(event: ProcessChangeEvent)(implicit user: LoggedUser): Unit = {

    implicit val listenerUser: User = ListenerApiUser(user)
    processChangeListener.handle(event)
  }

  private def createProcess(
      processName: ProcessName,
      parameters: ScenarioParameters,
      isFragment: Boolean,
      useLegacyCreateScenarioApi: Boolean
  )(implicit loggedUser: LoggedUser): Future[Either[NuDesignerError, Unit]] = if (useLegacyCreateScenarioApi) {
    processService
      .createProcess(
        CreateScenarioCommand(
          name = processName,
          category = Some(parameters.category),
          processingMode = Some(parameters.processingMode),
          engineSetupName = Some(parameters.engineSetupName),
          isFragment = isFragment,
        )
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

object MigrationService {

  private[MigrationService] def toMigrationError(
      nuDesignerError: NuDesignerError
  )(implicit loggedUser: LoggedUser): MigrationError =
    nuDesignerError match {
      case _: UnauthorizedError =>
        MigrationError.InsufficientPermission(loggedUser)
      case nuDesignerError: NuDesignerError =>
        throw new IllegalStateException(nuDesignerError.getMessage)
    }

  final case class MigrationApiAdapterError(apiAdapterError: ApiAdapterServiceError)
      extends FatalError(
        apiAdapterError match {
          case OutOfRangeAdapterRequestError(currentVersion, signedNoOfVersionsLeftToApply) =>
            signedNoOfVersionsLeftToApply match {
              case n if n >= 0 =>
                s"Migration API Adapter error occurred when trying to adapt MigrateScenarioRequest in version: $currentVersion to $signedNoOfVersionsLeftToApply version(s) up"
              case _ =>
                s"Migration API Adapter error occurred when trying to adapt MigrateScenarioRequest in version: $currentVersion to ${-signedNoOfVersionsLeftToApply} version(s) down"
            }
        }
      )

}
