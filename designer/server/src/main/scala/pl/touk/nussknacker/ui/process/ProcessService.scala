package pl.touk.nussknacker.ui.process

import cats._
import cats.syntax.functor._
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances.DB
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.{DataFreshnessPolicy, ProcessAction, ProcessActionType}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.process._
import pl.touk.nussknacker.ui.api.ProcessesResources.UnmarshallError
import pl.touk.nussknacker.ui.process.ProcessService._
import pl.touk.nussknacker.ui.process.deployment.DeploymentService
import pl.touk.nussknacker.ui.process.exception.{ProcessIllegalAction, ProcessValidationError}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.{
  ProcessNotFoundError,
  ProcessVersionNotFoundError
}
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.{
  CreateProcessAction,
  ProcessCreated,
  RemoteUserName,
  UpdateProcessAction
}
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolving
import pl.touk.nussknacker.ui.validation.{FatalValidationError, ProcessValidation}
import ScenarioWithDetailsConversions._
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.ui.listener.services.{RepositoryScenarioWithDetails, ScenarioShapeFetchStrategy}
import slick.dbio.DBIOAction

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

object ProcessService {

  @JsonCodec final case class CreateProcessCommand(
      processName: ProcessName,
      category: String,
      isFragment: Boolean,
      forwardedUserName: Option[RemoteUserName]
  )

  @JsonCodec final case class UpdateProcessCommand(
      process: DisplayableProcess,
      comment: UpdateProcessComment,
      forwardedUserName: Option[RemoteUserName]
  )

  object GetScenarioWithDetailsOptions {
    val detailsOnly: GetScenarioWithDetailsOptions =
      new GetScenarioWithDetailsOptions(SkipScenarioGraph, fetchState = false)

    val withsScenarioGraph: GetScenarioWithDetailsOptions =
      new GetScenarioWithDetailsOptions(FetchScenarioGraph(validateAndResolve = false), fetchState = false)
  }

  final case class GetScenarioWithDetailsOptions(fetchGraphOptions: ScenarioGraphOptions, fetchState: Boolean) {

    def withValidation: GetScenarioWithDetailsOptions = {
      val newFetchGraphOptions = fetchGraphOptions match {
        case SkipScenarioGraph => throw new IllegalStateException("withValidation used with SkipScenarioGraph option")
        case fetch: FetchScenarioGraph => fetch.copy(validateAndResolve = true)
      }
      copy(fetchGraphOptions = newFetchGraphOptions)
    }

    def withFetchState: GetScenarioWithDetailsOptions = copy(fetchState = true)
  }

  sealed trait ScenarioGraphOptions

  case object SkipScenarioGraph extends ScenarioGraphOptions

  case class FetchScenarioGraph(validateAndResolve: Boolean) extends ScenarioGraphOptions

}

trait ProcessService {
  def getProcessId(processName: ProcessName)(implicit ec: ExecutionContext): Future[ProcessId]

  def getProcessWithDetails(processId: ProcessIdWithName, options: GetScenarioWithDetailsOptions)(
      implicit user: LoggedUser
  ): Future[ScenarioWithDetails]

  def getProcessWithDetails(processId: ProcessIdWithName, versionId: VersionId, options: GetScenarioWithDetailsOptions)(
      implicit user: LoggedUser
  ): Future[ScenarioWithDetails]

  def getProcessesWithDetails(query: ProcessesQuery, options: GetScenarioWithDetailsOptions)(
      implicit user: LoggedUser
  ): Future[List[ScenarioWithDetails]]

  def getRawProcessesWithDetails[PS: ScenarioShapeFetchStrategy](query: ProcessesQuery)(
      implicit user: LoggedUser
  ): Future[List[RepositoryScenarioWithDetails[PS]]]

  def archiveProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[Unit]

  def deleteProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[Unit]

  def unArchiveProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[Unit]

  def renameProcess(processIdWithName: ProcessIdWithName, name: ProcessName)(
      implicit user: LoggedUser
  ): Future[UpdateProcessNameResponse]

  def updateCategory(processIdWithName: ProcessIdWithName, category: String)(
      implicit user: LoggedUser
  ): Future[UpdateProcessCategoryResponse]

  def createProcess(command: CreateProcessCommand)(implicit user: LoggedUser): Future[ProcessResponse]

  def updateProcess(processIdWithName: ProcessIdWithName, action: UpdateProcessCommand)(
      implicit user: LoggedUser
  ): Future[UpdateProcessResponse]

  def importProcess(processId: ProcessIdWithName, processData: String)(
      implicit user: LoggedUser
  ): Future[ValidatedDisplayableProcess]

  def getProcessActions(id: ProcessId): Future[List[ProcessAction]]

}

/**
 * ProcessService provides functionality for archive, unarchive, deploy, cancel process.
 * Each action includes verification based on actual process state and checking process is fragment / archived.
 */
class DBProcessService(
    deploymentService: DeploymentService,
    newProcessPreparer: NewProcessPreparer,
    getProcessCategoryService: () => ProcessCategoryService,
    processResolving: UIProcessResolving,
    dbioRunner: DBIOActionRunner,
    fetchingProcessRepository: FetchingProcessRepository[Future],
    processActionRepository: ProcessActionRepository[DB],
    processRepository: ProcessRepository[DB],
    processValidation: ProcessValidation
)(implicit ec: ExecutionContext)
    extends ProcessService
    with LazyLogging {

  override def getProcessId(processName: ProcessName)(implicit ec: ExecutionContext): Future[ProcessId] = {
    fetchingProcessRepository
      .fetchProcessId(processName)
      .map(_.getOrElse(throw ProcessNotFoundError(processName.toString)))
  }

  override def getProcessWithDetails(processIdWithName: ProcessIdWithName, options: GetScenarioWithDetailsOptions)(
      implicit user: LoggedUser
  ): Future[ScenarioWithDetails] = {
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    doGetProcessWithDetails(
      new FetchScenarioFun[Id] {
        override def apply[PS: ScenarioShapeFetchStrategy]: Future[Id[RepositoryScenarioWithDetails[PS]]] =
          fetchingProcessRepository
            .fetchLatestProcessDetailsForProcessId[PS](processIdWithName.id)
            .map(_.getOrElse(throw ProcessNotFoundError(processIdWithName.id.value.toString)))
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
        override def apply[PS: ScenarioShapeFetchStrategy]: Future[RepositoryScenarioWithDetails[PS]] =
          fetchingProcessRepository
            .fetchProcessDetailsForId[PS](processIdWithName.id, versionId)
            .map(_.getOrElse(throw ProcessVersionNotFoundError(processIdWithName.id, versionId)))
      },
      options
    )
  }

  override def getProcessesWithDetails(query: ProcessesQuery, options: GetScenarioWithDetailsOptions)(
      implicit user: LoggedUser
  ): Future[List[ScenarioWithDetails]] = {
    // To not overload engine, for list of processes we provide statuses that can be cached
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.CanBeCached
    doGetProcessWithDetails(
      new FetchScenarioFun[List] {
        override def apply[PS: ScenarioShapeFetchStrategy]: Future[List[RepositoryScenarioWithDetails[PS]]] =
          fetchingProcessRepository.fetchProcessesDetails(query)
      },
      options
    )
  }

  private abstract class FetchScenarioFun[F[_]] {
    def apply[PS: ScenarioShapeFetchStrategy]: Future[F[RepositoryScenarioWithDetails[PS]]]
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
          .map(_.map(ScenarioWithDetailsConversions.fromRepositoryDetailsIgnoringGraphAndValidationResult))
      case FetchScenarioGraph(validateAndResolve) =>
        fetchScenario[CanonicalProcess]
          .map(_.map(validateAndReverseResolve(_, validateAndResolve)))
    }).flatMap { details =>
      if (options.fetchState)
        deploymentService.enrichDetailsWithProcessState(details)
      else
        Future.successful(details)
    }
  }

  override def getRawProcessesWithDetails[PS: ScenarioShapeFetchStrategy](
      query: ProcessesQuery
  )(implicit user: LoggedUser): Future[List[RepositoryScenarioWithDetails[PS]]] = {
    fetchingProcessRepository.fetchProcessesDetails(query)
  }

  private def validateAndReverseResolve(
      processDetails: RepositoryScenarioWithDetails[CanonicalProcess],
      validateAndResolve: Boolean
  ): ScenarioWithDetails = {
    if (validateAndResolve) {
      validateAndReverseResolve(processDetails)
    } else {
      toDisplayableProcessDetailsWithoutValidation(processDetails)
    }
  }

  private def toDisplayableProcessDetailsWithoutValidation(
      canonicalProcessDetails: RepositoryScenarioWithDetails[CanonicalProcess]
  ): ScenarioWithDetails = {
    ScenarioWithDetailsConversions.fromRepositoryDetails(canonicalProcessDetails.mapScenario { canonical =>
      val displayableProcess = ProcessConverter.toDisplayable(
        canonical,
        canonicalProcessDetails.processingType,
        canonicalProcessDetails.processCategory
      )
      ValidatedDisplayableProcess.withEmptyValidationResult(displayableProcess)
    })
  }

  private def validateAndReverseResolve(
      processDetails: RepositoryScenarioWithDetails[CanonicalProcess]
  ): ScenarioWithDetails = {
    ScenarioWithDetailsConversions.fromRepositoryDetails(processDetails.mapScenario { canonical: CanonicalProcess =>
      val processingType = processDetails.processingType
      val validationResult =
        processResolving.validateBeforeUiReverseResolving(canonical, processingType, processDetails.processCategory)
      processResolving.reverseResolveExpressions(
        canonical,
        processingType,
        processDetails.processCategory,
        validationResult
      )
    })
  }

  override def archiveProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[Unit] =
    withNotArchivedProcess(processIdWithName, ProcessActionType.Archive) { process =>
      if (process.isFragment) {
        doArchive(process)
      } else {
        // FIXME: This doesn't work correctly because concurrent request can change a state and double archive actions will be done.
        //        See ManagementResourcesConcurrentSpec and how DeploymentService handles it correctly for deploy and cancel
        doOnProcessStateVerification(process, ProcessActionType.Archive)(doArchive(process))
      }
    }

  override def renameProcess(processIdWithName: ProcessIdWithName, name: ProcessName)(
      implicit user: LoggedUser
  ): Future[UpdateProcessNameResponse] =
    withNotArchivedProcess(processIdWithName, "Can't rename archived scenario.") { process =>
      if (process.isFragment) {
        doRename(processIdWithName, name)
      } else {
        doOnProcessStateVerification(process, ProcessActionType.Rename)(doRename(processIdWithName, name))
      }
    }

  override def unArchiveProcess(
      processIdWithName: ProcessIdWithName
  )(implicit user: LoggedUser): Future[Unit] =
    getProcessWithDetails(processIdWithName, GetScenarioWithDetailsOptions.detailsOnly).flatMap { process =>
      if (process.isArchived) {
        dbioRunner
          .runInTransaction(
            DBIOAction.seq(
              processRepository.archive(processId = process.idWithName.id, isArchived = false),
              processActionRepository
                .markProcessAsUnArchived(processId = process.idWithName.id, process.processVersionId)
            )
          )
      } else {
        throw ProcessIllegalAction("Can't unarchive not archived scenario.")
      }
    }

  override def deleteProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[Unit] =
    withArchivedProcess(processIdWithName, "Can't delete not archived scenario.") {
      dbioRunner
        .runInTransaction(processRepository.deleteProcess(processIdWithName.id))
    }

  override def updateCategory(processIdWithName: ProcessIdWithName, category: String)(
      implicit user: LoggedUser
  ): Future[UpdateProcessCategoryResponse] =
    withNotArchivedProcess(processIdWithName, "Can't update category archived scenario.") { process =>
      withProcessingType(category) { _ =>
        dbioRunner.runInTransaction(
          processRepository
            .updateCategory(processIdWithName.id, category)
            .map(_ => UpdateProcessCategoryResponse(process.processCategory, category))
        )
      }
    }

  // FIXME: Create process should create two inserts (process, processVersion) in transactional way, but right we do all in process repository..
  override def createProcess(
      command: CreateProcessCommand
  )(implicit user: LoggedUser): Future[ProcessResponse] =
    withProcessingType(command.category) { processingType =>
      val emptyCanonicalProcess =
        newProcessPreparer.prepareEmptyProcess(command.processName.value, processingType, command.isFragment)
      val action = CreateProcessAction(
        command.processName,
        command.category,
        emptyCanonicalProcess,
        processingType,
        command.isFragment,
        command.forwardedUserName
      )

      val propertiesErrors = validateInitialScenarioProperties(emptyCanonicalProcess, processingType, command.category)

      if (propertiesErrors.nonEmpty) {
        throw ProcessValidationError(propertiesErrors.map(_.message).mkString(", "))
      } else {
        dbioRunner
          .runInTransaction(processRepository.saveNewProcess(action))
          .map { maybeCreated =>
            maybeCreated
              .map(created => toProcessResponse(command.processName, created))
              .getOrElse(throw ProcessValidationError("Unknown error on creating scenario."))
          }
      }
    }

  // FIXME: Update process should update process and create process version in transactional way, but right we do all in process repository..
  override def updateProcess(processIdWithName: ProcessIdWithName, action: UpdateProcessCommand)(
      implicit user: LoggedUser
  ): Future[UpdateProcessResponse] =
    withNotArchivedProcess(processIdWithName, "Can't update graph archived scenario.") { _ =>
      val validation =
        FatalValidationError.saveNotAllowedAsError(processResolving.validateBeforeUiResolving(action.process))
      val substituted = processResolving.resolveExpressions(action.process, validation.typingInfo)
      val updateProcessAction = UpdateProcessAction(
        processIdWithName.id,
        substituted,
        Option(action.comment),
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

  def importProcess(processId: ProcessIdWithName, jsonString: String)(
      implicit user: LoggedUser
  ): Future[ValidatedDisplayableProcess] = {
    withNotArchivedProcess(processId, "Import is not allowed for archived process.") { process =>
      val jsonCanonicalProcess = ProcessMarshaller
        .fromJson(jsonString)
        .valueOr(msg => throw UnmarshallError(msg))

      val canonical   = jsonCanonicalProcess.withProcessId(processId.name)
      val displayable = ProcessConverter.toDisplayable(canonical, process.processingType, process.processCategory)
      val validationResult = processResolving.validateBeforeUiReverseResolving(
        canonical,
        displayable.processingType,
        process.processCategory
      )
      Future.successful(ValidatedDisplayableProcess(displayable, validationResult))
    }
  }

  private def validateInitialScenarioProperties(
      canonicalProcess: CanonicalProcess,
      processingType: ProcessingType,
      category: String
  ) = {
    val validationResult =
      processValidation.processingTypeValidationWithTypingInfo(canonicalProcess, processingType, category)
    validationResult.errors.processPropertiesErrors

  }

  private def doOnProcessStateVerification[T](process: ScenarioWithDetails, actionToCheck: ProcessActionType)(
      callback: => Future[T]
  )(implicit user: LoggedUser): Future[T] = {
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    deploymentService
      .getProcessState(process.toRepositoryDetailsWithoutScenarioGraphAndValidationResult)
      .flatMap(state => {
        if (state.allowedActions.contains(actionToCheck)) {
          callback
        } else {
          throw ProcessIllegalAction(actionToCheck, process.idWithName, state)
        }
      })
  }

  private def doArchive(process: ScenarioWithDetails)(implicit user: LoggedUser): Future[Unit] =
    dbioRunner
      .runInTransaction(
        DBIOAction.seq(
          processRepository.archive(processId = process.idWithName.id, isArchived = true),
          processActionRepository.markProcessAsArchived(processId = process.idWithName.id, process.processVersionId)
        )
      )

  private def doRename(processIdWithName: ProcessIdWithName, name: ProcessName)(implicit user: LoggedUser) = {
    dbioRunner.runInTransaction(
      processRepository
        .renameProcess(processIdWithName, name)
        .map(_ => UpdateProcessNameResponse.create(processIdWithName.name.value, name.value))
    )
  }

  override def getProcessActions(id: ProcessId): Future[List[ProcessAction]] = {
    dbioRunner.runInTransaction(processActionRepository.getFinishedProcessActions(id, None))
  }

  private def toProcessResponse(processName: ProcessName, created: ProcessCreated): ProcessResponse =
    ProcessResponse(created.processId, created.processVersionId, processName)

  private def withArchivedProcess[T](processIdWithName: ProcessIdWithName, errorMessage: String)(
      callback: => Future[T]
  )(implicit user: LoggedUser): Future[T] = {
    getProcessWithDetails(processIdWithName, GetScenarioWithDetailsOptions.detailsOnly).flatMap { process =>
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
    getProcessWithDetails(processIdWithName, GetScenarioWithDetailsOptions.detailsOnly).flatMap { process =>
      if (process.isArchived) {
        throw ProcessIllegalAction(errorMessage)
      } else {
        callback(process)
      }
    }
  }

  private def withNotArchivedProcess[T](processIdWithName: ProcessIdWithName, action: ProcessActionType)(
      callback: ScenarioWithDetails => Future[T]
  )(implicit user: LoggedUser): Future[T] =
    getProcessWithDetails(processIdWithName, GetScenarioWithDetailsOptions.detailsOnly).flatMap { process =>
      if (process.isArchived) {
        throw ProcessIllegalAction.archived(action, process.idWithName)
      } else {
        callback(process)
      }
    }

  private def withProcessingType[T](
      category: String
  )(callback: ProcessingType => Future[T]): Future[T] =
    getProcessCategoryService().getTypeForCategory(category) match {
      case Some(processingType) =>
        callback(processingType)
      case None =>
        throw ProcessValidationError("Scenario category not found.")
    }

}
