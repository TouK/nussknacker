package pl.touk.nussknacker.ui.process

import cats._
import cats.data.Validated
import cats.implicits.toTraverseOps
import cats.syntax.functor._
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances.DB
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.deployment.{DataFreshnessPolicy, ProcessAction, ScenarioActionName}
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.{Comment, NonEmptyComment, ProcessVersion}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.restmodel.process._
import pl.touk.nussknacker.restmodel.scenariodetails.{BaseCreateScenarioCommand, ScenarioWithDetails}
import pl.touk.nussknacker.restmodel.validation.ScenarioGraphWithValidationResult
import pl.touk.nussknacker.ui.NuDesignerError
import pl.touk.nussknacker.ui.api.ProcessesResources.ProcessUnmarshallingError
import pl.touk.nussknacker.ui.process.ProcessService._
import pl.touk.nussknacker.ui.process.ScenarioWithDetailsConversions._
import pl.touk.nussknacker.ui.process.exception.{ProcessIllegalAction, ProcessValidationError}
import pl.touk.nussknacker.ui.process.label.ScenarioLabel
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.process.processingtype.ScenarioParametersService
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.{
  ProcessNotFoundError,
  ProcessVersionNotFoundError
}
import pl.touk.nussknacker.ui.process.repository.ProcessRepository._
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver
import pl.touk.nussknacker.ui.validation.FatalValidationError
import slick.dbio.DBIOAction

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

object ProcessService {

  // TODO For scripting purpose we should also support optional scenarioGraph field to avoid creating and than updating each time
  @JsonCodec final case class CreateScenarioCommand(
      override val name: ProcessName,
      category: Option[String],
      processingMode: Option[ProcessingMode],
      engineSetupName: Option[EngineSetupName],
      override val isFragment: Boolean,
      forwardedUserName: Option[RemoteUserName]
  ) extends BaseCreateScenarioCommand

  @JsonCodec final case class UpdateScenarioCommand(
      scenarioGraph: ScenarioGraph,
      comment: Option[Comment],
      scenarioLabels: Option[List[String]],
      forwardedUserName: Option[RemoteUserName]
  )

  final case class MigrateScenarioCommand(
      scenarioGraph: ScenarioGraph,
      scenarioLabels: Option[List[String]],
      forwardedUserName: Option[RemoteUserName],
      sourceEnvironment: String,
      targetEnvironment: String,
      sourceScenarioVersionId: Option[VersionId],
  )

  object GetScenarioWithDetailsOptions {
    val detailsOnly: GetScenarioWithDetailsOptions =
      new GetScenarioWithDetailsOptions(SkipScenarioGraph, fetchState = false)

    val withsScenarioGraph: GetScenarioWithDetailsOptions =
      new GetScenarioWithDetailsOptions(FetchScenarioGraph(), fetchState = false)
  }

  final case class GetScenarioWithDetailsOptions(fetchGraphOptions: ScenarioGraphOptions, fetchState: Boolean) {

    def withValidation: GetScenarioWithDetailsOptions = {
      val newFetchGraphOptions = fetchGraphOptions match {
        case SkipScenarioGraph => throw new IllegalStateException("withValidation used with SkipScenarioGraph option")
        case fetch: FetchScenarioGraph => fetch.copy(validate = FetchScenarioGraph.ValidateAndResolve())
      }
      copy(fetchGraphOptions = newFetchGraphOptions)
    }

    def withFetchState: GetScenarioWithDetailsOptions = copy(fetchState = true)
  }

  sealed trait ScenarioGraphOptions

  case object SkipScenarioGraph extends ScenarioGraphOptions

  case class FetchScenarioGraph(validate: FetchScenarioGraph.ValidationMode = FetchScenarioGraph.DontValidate)
      extends ScenarioGraphOptions

  object FetchScenarioGraph {
    sealed trait ValidationMode {}

    case object DontValidate extends ValidationMode

    case class ValidateAndResolve(includeValidationNodeResults: Boolean = true) extends ValidationMode
  }

}

trait ProcessService {
  def getProcessIdUnsafe(processName: ProcessName)(implicit ec: ExecutionContext): Future[ProcessId]

  def getProcessId(scenarioName: ProcessName)(implicit ec: ExecutionContext): Future[Option[ProcessId]]

  def getLatestProcessWithDetails(processId: ProcessIdWithName, options: GetScenarioWithDetailsOptions)(
      implicit user: LoggedUser
  ): Future[ScenarioWithDetails]

  def getProcessWithDetails(processId: ProcessIdWithName, versionId: VersionId, options: GetScenarioWithDetailsOptions)(
      implicit user: LoggedUser
  ): Future[ScenarioWithDetails]

  def getLatestProcessesWithDetails(query: ScenarioQuery, options: GetScenarioWithDetailsOptions)(
      implicit user: LoggedUser
  ): Future[List[ScenarioWithDetails]]

  def getLatestRawProcessesWithDetails[PS: ScenarioShapeFetchStrategy](query: ScenarioQuery)(
      implicit user: LoggedUser
  ): Future[List[ScenarioWithDetailsEntity[PS]]]

  def archiveProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[Unit]

  def deleteProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[Unit]

  def unArchiveProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[Unit]

  def renameProcess(processIdWithName: ProcessIdWithName, name: ProcessName)(
      implicit user: LoggedUser
  ): Future[UpdateProcessNameResponse]

  def createProcess(command: CreateScenarioCommand)(
      implicit user: LoggedUser
  ): Future[Validated[NuDesignerError, ProcessResponse]]

  def updateProcess(processIdWithName: ProcessIdWithName, action: UpdateScenarioCommand)(
      implicit user: LoggedUser
  ): Future[UpdateProcessResponse]

  def migrateProcess(processIdWithName: ProcessIdWithName, action: MigrateScenarioCommand)(
      implicit user: LoggedUser
  ): Future[UpdateProcessResponse]

  def importProcess(processId: ProcessIdWithName, processData: String)(
      implicit user: LoggedUser
  ): Future[ScenarioGraphWithValidationResult]

  def getProcessActions(id: ProcessId): Future[List[ProcessAction]]

}

/**
 * ProcessService provides functionality for archive, unarchive, deploy, cancel process.
 * Each action includes verification based on actual process state and checking process is fragment / archived.
 */
class DBProcessService(
    processStateProvider: ProcessStateProvider,
    newProcessPreparers: ProcessingTypeDataProvider[NewProcessPreparer, _],
    scenarioParametersServiceProvider: ProcessingTypeDataProvider[_, ScenarioParametersService],
    processResolverByProcessingType: ProcessingTypeDataProvider[UIProcessResolver, _],
    dbioRunner: DBIOActionRunner,
    fetchingProcessRepository: FetchingProcessRepository[Future],
    scenarioActionRepository: ScenarioActionRepository,
    processRepository: ProcessRepository[DB]
)(implicit ec: ExecutionContext)
    extends ProcessService
    with LazyLogging {

  override def getProcessIdUnsafe(processName: ProcessName)(implicit ec: ExecutionContext): Future[ProcessId] = {
    getProcessId(processName)
      .map(_.getOrElse(throw ProcessNotFoundError(processName)))
  }

  override def getProcessId(scenarioName: ProcessName)(implicit ec: ExecutionContext): Future[Option[ProcessId]] = {
    fetchingProcessRepository.fetchProcessId(scenarioName)
  }

  override def getLatestProcessWithDetails(
      processIdWithName: ProcessIdWithName,
      options: GetScenarioWithDetailsOptions
  )(
      implicit user: LoggedUser
  ): Future[ScenarioWithDetails] = {
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    doGetProcessWithDetails(
      new FetchScenarioFun[Id] {
        override def apply[PS: ScenarioShapeFetchStrategy]: Future[Id[ScenarioWithDetailsEntity[PS]]] =
          fetchingProcessRepository
            .fetchLatestProcessDetailsForProcessId[PS](processIdWithName.id)
            .map(_.getOrElse(throw ProcessNotFoundError(processIdWithName.name)))
      },
      options
    )
  }

  override def getProcessWithDetails(
      processIdWithName: ProcessIdWithName,
      versionId: VersionId,
      options: GetScenarioWithDetailsOptions
  )(
      implicit user: LoggedUser
  ): Future[ScenarioWithDetails] = {
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    doGetProcessWithDetails(
      new FetchScenarioFun[Id] {
        override def apply[PS: ScenarioShapeFetchStrategy]: Future[ScenarioWithDetailsEntity[PS]] =
          fetchingProcessRepository
            .fetchProcessDetailsForId[PS](processIdWithName.id, versionId)
            .map(_.getOrElse(throw ProcessVersionNotFoundError(processIdWithName.name, versionId)))
      },
      options
    )
  }

  override def getLatestProcessesWithDetails(query: ScenarioQuery, options: GetScenarioWithDetailsOptions)(
      implicit user: LoggedUser
  ): Future[List[ScenarioWithDetails]] = {
    // To not overload engine, for list of processes we provide statuses that can be cached
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.CanBeCached
    doGetProcessWithDetails(
      new FetchScenarioFun[List] {
        override def apply[PS: ScenarioShapeFetchStrategy]: Future[List[ScenarioWithDetailsEntity[PS]]] =
          fetchingProcessRepository.fetchLatestProcessesDetails(query)
      },
      options
    )
  }

  private abstract class FetchScenarioFun[F[_]] {
    def apply[PS: ScenarioShapeFetchStrategy]: Future[F[ScenarioWithDetailsEntity[PS]]]
  }

  private def doGetProcessWithDetails[F[_]: Traverse](
      fetchScenario: FetchScenarioFun[F],
      options: GetScenarioWithDetailsOptions
  )(
      implicit user: LoggedUser,
      freshnessPolicy: DataFreshnessPolicy
  ): Future[F[ScenarioWithDetails]] = {
    (options.fetchGraphOptions match {
      case SkipScenarioGraph =>
        fetchScenario[Unit]
          .map(_.map { e =>
            val parameters =
              scenarioParametersServiceProvider.combined.getParametersWithReadPermissionUnsafe(e.processingType)
            ScenarioWithDetailsConversions.fromEntityIgnoringGraphAndValidationResult(e, parameters)
          })
      case FetchScenarioGraph(validate) =>
        fetchScenario[CanonicalProcess]
          .map(_.map(validateAndReverseResolve(_, validate)))
    }).flatMap { details =>
      if (options.fetchState)
        processStateProvider.enrichDetailsWithProcessState(details)
      else
        Future.successful(details)
    }
  }

  override def getLatestRawProcessesWithDetails[PS: ScenarioShapeFetchStrategy](
      query: ScenarioQuery
  )(implicit user: LoggedUser): Future[List[ScenarioWithDetailsEntity[PS]]] = {
    fetchingProcessRepository.fetchLatestProcessesDetails(query)
  }

  private def validateAndReverseResolve(
      entity: ScenarioWithDetailsEntity[CanonicalProcess],
      validate: FetchScenarioGraph.ValidationMode
  )(implicit user: LoggedUser): ScenarioWithDetails = {
    validate match {
      case FetchScenarioGraph.ValidateAndResolve(true) =>
        validateAndReverseResolve(entity)
      case FetchScenarioGraph.ValidateAndResolve(false) =>
        // reduce serialized response JSON by ~5-10 MB (typical typing information size in production use)
        val result = validateAndReverseResolve(entity)
        result.copy(validationResult = result.validationResult.map(_.copy(nodeResults = Map.empty)))
      case FetchScenarioGraph.DontValidate =>
        toDisplayableProcessDetailsWithoutValidation(entity)
    }
  }

  private def toDisplayableProcessDetailsWithoutValidation(
      entity: ScenarioWithDetailsEntity[CanonicalProcess]
  )(implicit user: LoggedUser): ScenarioWithDetails = {
    val parameters =
      scenarioParametersServiceProvider.combined.getParametersWithReadPermissionUnsafe(entity.processingType)
    ScenarioWithDetailsConversions.fromEntityWithScenarioGraph(
      entity.mapScenario { canonical =>
        CanonicalProcessConverter.toScenarioGraph(canonical)
      },
      parameters
    )
  }

  private def validateAndReverseResolve(
      entity: ScenarioWithDetailsEntity[CanonicalProcess]
  )(implicit user: LoggedUser): ScenarioWithDetails = {
    val parameters =
      scenarioParametersServiceProvider.combined.getParametersWithReadPermissionUnsafe(entity.processingType)
    ScenarioWithDetailsConversions.fromEntity(
      entity.mapScenario { canonical: CanonicalProcess =>
        val processResolver = processResolverByProcessingType.forProcessingTypeUnsafe(entity.processingType)
        processResolver.validateAndReverseResolve(
          canonical,
          entity.toEngineProcessVersion,
          entity.isFragment,
        )
      },
      parameters
    )
  }

  override def archiveProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[Unit] =
    actionOnNotArchivedProcess(processIdWithName, ScenarioActionName.Archive) { process =>
      if (process.isFragment) {
        doArchive(process)
      } else {
        // FIXME: This doesn't work correctly because concurrent request can change a state and double archive actions will be done.
        //        See ManagementResourcesConcurrentSpec and how DeploymentService handles it correctly for deploy and cancel
        doOnProcessStateVerification(process, ScenarioActionName.Archive)(doArchive(process))
      }
    }

  override def renameProcess(processIdWithName: ProcessIdWithName, name: ProcessName)(
      implicit user: LoggedUser
  ): Future[UpdateProcessNameResponse] =
    withNotArchivedProcess(processIdWithName, "Can't rename archived scenario.") { process =>
      if (process.isFragment) {
        doRename(processIdWithName, name)
      } else {
        doOnProcessStateVerification(process, ScenarioActionName.Rename)(doRename(processIdWithName, name))
      }
    }

  override def unArchiveProcess(
      processIdWithName: ProcessIdWithName
  )(implicit user: LoggedUser): Future[Unit] =
    getLatestProcessWithDetails(processIdWithName, GetScenarioWithDetailsOptions.detailsOnly).flatMap { process =>
      if (process.isArchived) {
        dbioRunner
          .runInTransaction(
            DBIOAction.seq(
              processRepository.archive(processId = process.idWithNameUnsafe, isArchived = false),
              scenarioActionRepository
                .markProcessAsUnArchived(processId = process.processIdUnsafe, process.processVersionId)
            )
          )
      } else {
        throw ProcessIllegalAction("Can't unarchive not archived scenario.")
      }
    }

  override def deleteProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[Unit] =
    withArchivedProcess(processIdWithName, "Can't delete not archived scenario.") {
      dbioRunner
        .runInTransaction(processRepository.deleteProcess(processIdWithName))
    }

  // FIXME: Create process should create two inserts (process, processVersion) in transactional way, but right we do all in process repository..
  override def createProcess(
      command: CreateScenarioCommand
  )(implicit user: LoggedUser): Future[Validated[NuDesignerError, ProcessResponse]] = {
    val scenarioParametersService = scenarioParametersServiceProvider.combined
    scenarioParametersService
      .queryProcessingTypeWithWritePermission(command.category, command.processingMode, command.engineSetupName)
      .map { processingType =>
        val newProcessPreparer = newProcessPreparers.forProcessingTypeUnsafe(processingType)
        val emptyCanonicalProcess =
          newProcessPreparer.prepareEmptyProcess(command.name, command.isFragment)
        val action = CreateProcessAction(
          command.name,
          command.category.getOrElse(scenarioParametersService.categoryUnsafe(processingType)),
          emptyCanonicalProcess,
          processingType,
          command.isFragment,
          command.forwardedUserName
        )

        val propertiesErrors =
          validateInitialScenarioProperties(emptyCanonicalProcess, processingType, command.isFragment)

        if (propertiesErrors.nonEmpty) {
          throw ProcessValidationError(propertiesErrors.map(_.message).mkString(", "))
        } else {
          dbioRunner
            .runInTransaction(processRepository.saveNewProcess(action))
            .map { maybeCreated =>
              maybeCreated
                .map(created => toProcessResponse(command.name, created))
                .getOrElse(throw ProcessValidationError("Unknown error on creating scenario."))
            }
        }
      }
      .sequence
  }

  // FIXME: Update process should update process and create process version in transactional way, but right we do all in process repository..
  override def updateProcess(processIdWithName: ProcessIdWithName, action: UpdateScenarioCommand)(
      implicit user: LoggedUser
  ): Future[UpdateProcessResponse] =
    withNotArchivedProcess(processIdWithName, "Can't update graph archived scenario.") { details =>
      val processResolver = processResolverByProcessingType.forProcessingTypeUnsafe(details.processingType)
      val scenarioLabels  = action.scenarioLabels.getOrElse(List.empty).map(ScenarioLabel.apply)
      val validation =
        FatalValidationError.saveNotAllowedAsError(
          processResolver.validateBeforeUiResolving(
            action.scenarioGraph,
            details.processVersionUnsafe,
            details.isFragment,
          )
        )
      val substituted = processResolver.resolveExpressions(action.scenarioGraph, details.name, validation.typingInfo)
      val updateProcessAction = UpdateProcessAction(
        processId = processIdWithName.id,
        canonicalProcess = substituted,
        comment = action.comment.flatMap(NonEmptyComment.from),
        labels = scenarioLabels,
        increaseVersionWhenJsonNotChanged = false,
        forwardedUserName = action.forwardedUserName
      )
      dbioRunner
        .runInTransaction(processRepository.updateProcess(updateProcessAction))
        .map { processUpdated =>
          UpdateProcessResponse(
            processUpdated.newVersion
              .map(ProcessCreated(processIdWithName.id, _))
              .map(toProcessResponse(processIdWithName.name, _)),
            validation
          )
        }
    }

  override def migrateProcess(processIdWithName: ProcessIdWithName, action: MigrateScenarioCommand)(
      implicit user: LoggedUser
  ): Future[UpdateProcessResponse] = {
    withNotArchivedProcess(processIdWithName, "Can't migrate graph archived scenario.") { details =>
      val processResolver = processResolverByProcessingType.forProcessingTypeUnsafe(details.processingType)
      val scenarioLabels  = action.scenarioLabels.getOrElse(List.empty).map(ScenarioLabel.apply)
      val validation =
        FatalValidationError.saveNotAllowedAsError(
          processResolver.validateBeforeUiResolving(
            action.scenarioGraph,
            details.name,
            details.isFragment,
            scenarioLabels
          )
        )
      val substituted = processResolver.resolveExpressions(action.scenarioGraph, details.name, validation.typingInfo)
      val migrateProcessAction = MigrateProcessAction(
        processId = processIdWithName.id,
        canonicalProcess = substituted,
        labels = scenarioLabels,
        increaseVersionWhenJsonNotChanged = false,
        forwardedUserName = action.forwardedUserName,
        sourceEnvironment = action.sourceEnvironment,
        targetEnvironment = action.targetEnvironment,
        sourceScenarioVersionId = action.sourceScenarioVersionId,
      )
      dbioRunner
        .runInTransaction(processRepository.migrateProcess(migrateProcessAction))
        .map { processUpdated =>
          UpdateProcessResponse(
            processUpdated.newVersion
              .map(ProcessCreated(processIdWithName.id, _))
              .map(toProcessResponse(processIdWithName.name, _)),
            validation
          )
        }
    }
  }

  def importProcess(processId: ProcessIdWithName, jsonString: String)(
      implicit user: LoggedUser
  ): Future[ScenarioGraphWithValidationResult] = {
    withNotArchivedProcess(processId, "Import is not allowed for archived process.") { process =>
      val jsonCanonicalProcess = ProcessMarshaller
        .fromJson(jsonString)
        .valueOr(msg => throw ProcessUnmarshallingError(msg))

      val canonical     = jsonCanonicalProcess.withProcessName(processId.name)
      val scenarioGraph = CanonicalProcessConverter.toScenarioGraph(canonical)
      val validationResult = processResolverByProcessingType
        .forProcessingTypeUnsafe(process.processingType)
        .validateBeforeUiReverseResolving(canonical, process.processVersionUnsafe, process.isFragment)
      Future.successful(ScenarioGraphWithValidationResult(scenarioGraph, validationResult))
    }
  }

  private def validateInitialScenarioProperties(
      canonicalProcess: CanonicalProcess,
      processingType: ProcessingType,
      isFragment: Boolean
  )(implicit user: LoggedUser) = {
    val newProcessVersion = ProcessVersion.empty.copy(processName = canonicalProcess.name)
    val validationResult =
      processResolverByProcessingType
        .forProcessingTypeUnsafe(processingType)
        .validateBeforeUiReverseResolving(canonicalProcess, newProcessVersion, isFragment)
    validationResult.errors.processPropertiesErrors
  }

  private def doOnProcessStateVerification[T](process: ScenarioWithDetails, actionToCheck: ScenarioActionName)(
      callback: => Future[T]
  )(implicit user: LoggedUser): Future[T] = {
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    processStateProvider
      .getProcessState(process.toEntity)
      .flatMap(state => {
        if (state.allowedActions.contains(actionToCheck)) {
          callback
        } else {
          throw ProcessIllegalAction(actionToCheck, process.name, state)
        }
      })
  }

  private def doArchive(process: ScenarioWithDetails)(implicit user: LoggedUser): Future[Unit] =
    dbioRunner
      .runInTransaction(
        DBIOAction.seq(
          processRepository.archive(processId = process.idWithNameUnsafe, isArchived = true),
          scenarioActionRepository.markProcessAsArchived(processId = process.processIdUnsafe, process.processVersionId)
        )
      )

  private def doRename(processIdWithName: ProcessIdWithName, name: ProcessName)(implicit user: LoggedUser) = {
    dbioRunner.runInTransaction(
      processRepository
        .renameProcess(processIdWithName, name)
        .map(_ => UpdateProcessNameResponse.create(processIdWithName.name, name))
    )
  }

  override def getProcessActions(id: ProcessId): Future[List[ProcessAction]] = {
    dbioRunner.runInTransaction(scenarioActionRepository.getFinishedProcessActions(id, None))
  }

  private def toProcessResponse(processName: ProcessName, created: ProcessCreated): ProcessResponse =
    ProcessResponse(created.processId, created.processVersionId, processName)

  private def withArchivedProcess[T](processIdWithName: ProcessIdWithName, errorMessage: String)(
      callback: => Future[T]
  )(implicit user: LoggedUser): Future[T] = {
    getLatestProcessWithDetails(processIdWithName, GetScenarioWithDetailsOptions.detailsOnly).flatMap { process =>
      if (process.isArchived) {
        callback
      } else {
        throw ProcessIllegalAction(errorMessage)
      }
    }
  }

  private def withNotArchivedProcess[T](processIdWithName: ProcessIdWithName, errorMessage: String)(
      callback: ScenarioWithDetails => Future[T]
  )(implicit user: LoggedUser): Future[T] = {
    getLatestProcessWithDetails(processIdWithName, GetScenarioWithDetailsOptions.detailsOnly).flatMap { process =>
      if (process.isArchived) {
        throw ProcessIllegalAction(errorMessage)
      } else {
        callback(process)
      }
    }
  }

  private def actionOnNotArchivedProcess[T](processIdWithName: ProcessIdWithName, action: ScenarioActionName)(
      callback: ScenarioWithDetails => Future[T]
  )(implicit user: LoggedUser): Future[T] =
    getLatestProcessWithDetails(processIdWithName, GetScenarioWithDetailsOptions.detailsOnly).flatMap { process =>
      if (process.isArchived) {
        throw ProcessIllegalAction.archived(action, process.name)
      } else {
        callback(process)
      }
    }

}
