package pl.touk.nussknacker.ui.process

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
import pl.touk.nussknacker.restmodel.processdetails.{
  BaseProcessDetails,
  ProcessShapeFetchStrategy,
  ValidatedProcessDetails
}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import pl.touk.nussknacker.ui.api.ProcessesQuery
import pl.touk.nussknacker.ui.api.ProcessesResources.UnmarshallError
import pl.touk.nussknacker.ui.process.ProcessService.{CreateProcessCommand, UpdateProcessCommand}
import pl.touk.nussknacker.ui.process.deployment.DeploymentService
import pl.touk.nussknacker.ui.process.exception.{ProcessIllegalAction, ProcessValidationError}
import pl.touk.nussknacker.ui.process.fragment.FragmentDetails
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository.FetchProcessesDetailsQuery
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

}

trait ProcessService {
  def getProcessId(processName: ProcessName)(implicit ec: ExecutionContext): Future[ProcessId]

  // Below we have many different strategies to provide processes and/or their details
  // "raw" variant means that we don't do state enrichement, ui resolution and validation
  // "details only" variant means that we don't fetch scenario json at all - only details
  // "get process with details" variant means that we do all things
  // TODO: reduce number of these variants
  def getProcessDetailsOnly(processIdWithName: ProcessIdWithName)(
      implicit user: LoggedUser
  ): Future[BaseProcessDetails[Unit]]

  def getProcessDetailsWithStateOnly(query: ProcessesQuery)(
      implicit user: LoggedUser
  ): Future[List[BaseProcessDetails[Unit]]]

  // Do we really need raw version of method that doesn't do state enrichement, ui resolution and validation?
  def getRawProcessWithDetails(processId: ProcessIdWithName, versionId: VersionId)(
      implicit user: LoggedUser
  ): Future[BaseProcessDetails[DisplayableProcess]]

  def getRawProcessesWithDetails[PS: ProcessShapeFetchStrategy](
      isFragment: Option[Boolean],
      isArchived: Option[Boolean]
  )(implicit user: LoggedUser): Future[List[BaseProcessDetails[PS]]]

  def getProcessWithDetails(processId: ProcessIdWithName, skipValidateAndResolve: Boolean)(
      implicit user: LoggedUser
  ): Future[ValidatedProcessDetails]

  def getProcessWithDetails(processId: ProcessIdWithName, versionId: VersionId, skipValidateAndResolve: Boolean)(
      implicit user: LoggedUser
  ): Future[ValidatedProcessDetails]

  def getProcessesWithDetails(query: ProcessesQuery, skipValidateAndResolve: Boolean)(
      implicit user: LoggedUser
  ): Future[List[ValidatedProcessDetails]]

  def getFragmentsDetails(processingTypes: Option[List[ProcessingType]])(
      implicit user: LoggedUser
  ): Future[Set[FragmentDetails]]

  // END OF GET PROCESS AND / OR DETAILS

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

  override def getProcessDetailsOnly(
      processIdWithName: ProcessIdWithName
  )(implicit user: LoggedUser): Future[BaseProcessDetails[Unit]] =
    fetchingProcessRepository
      .fetchLatestProcessDetailsForProcessId[Unit](processIdWithName.id)
      .map(_.getOrElse(throw ProcessNotFoundError(processIdWithName.id.value.toString)))

  override def getProcessDetailsWithStateOnly(
      query: ProcessesQuery
  )(implicit user: LoggedUser): Future[List[BaseProcessDetails[Unit]]] = {
    // To not overload engine, for list of processes we provide statuses that can be cached
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.CanBeCached
    fetchingProcessRepository
      .fetchProcessesDetails[Unit](query.toRepositoryQuery)
      .flatMap(deploymentService.enrichDetailsWithProcessState)
  }

  override def getRawProcessWithDetails(processId: ProcessIdWithName, versionId: VersionId)(
      implicit user: LoggedUser
  ): Future[BaseProcessDetails[DisplayableProcess]] = {
    fetchingProcessRepository
      .fetchProcessDetailsForId[DisplayableProcess](processId.id, versionId)
      .map(_.getOrElse(throw ProcessVersionNotFoundError(processId.id, versionId)))
  }

  override def getRawProcessesWithDetails[PS: ProcessShapeFetchStrategy](
      isFragment: Option[Boolean],
      isArchived: Option[Boolean]
  )(implicit user: LoggedUser): Future[List[BaseProcessDetails[PS]]] = {
    val userCategoryService = new UserCategoryService(getProcessCategoryService())
    val userCategories = userCategoryService.getUserCategories(user)
    fetchingProcessRepository.fetchProcessesDetails(
      FetchProcessesDetailsQuery(isFragment = isFragment, isArchived = isArchived, categories = Some(userCategories))
    )
  }

  override def getProcessWithDetails(processId: ProcessIdWithName, skipValidateAndResolve: Boolean)(
      implicit user: LoggedUser
  ): Future[ValidatedProcessDetails] = {
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    fetchingProcessRepository
      .fetchLatestProcessDetailsForProcessId[CanonicalProcess](processId.id)
      .flatMap(enrichWithStateAndThenValidateAndReverseResolve(processId, _, skipValidateAndResolve))
  }

  override def getProcessWithDetails(
      processId: ProcessIdWithName,
      versionId: VersionId,
      skipValidateAndResolve: Boolean
  )(
      implicit user: LoggedUser
  ): Future[ValidatedProcessDetails] = {
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    fetchingProcessRepository
      .fetchProcessDetailsForId[CanonicalProcess](processId.id, versionId)
      // TODO: Here we enrich state but method is ran for some specific version. Why? State is assigned to scenario, not version.
      .flatMap(enrichWithStateAndThenValidateAndReverseResolve(processId, _, skipValidateAndResolve))
  }

  override def getProcessesWithDetails(query: ProcessesQuery, skipValidateAndResolve: Boolean)(
      implicit user: LoggedUser
  ): Future[List[ValidatedProcessDetails]] = {
    fetchingProcessRepository.fetchProcessesDetails[CanonicalProcess](query.toRepositoryQuery).map { processes =>
      // TODO: In method variant with processId we do enrichDetailsWithProcessState as well, but here not. Is it ok?
      processes.map(validateAndReverseResolve(_, skipValidateAndResolve))
    }
  }

  private def enrichWithStateAndThenValidateAndReverseResolve(
      processId: ProcessIdWithName,
      canonicalProcessOpt: Option[BaseProcessDetails[CanonicalProcess]],
      skipValidateAndResolve: Boolean
  )(implicit user: LoggedUser, freshnessPolicy: DataFreshnessPolicy) = {
    val canonicalProcess = canonicalProcessOpt.getOrElse(throw ProcessNotFoundError(processId.id.toString))
    enrichDetailsWithProcessState(canonicalProcess)
      .map(validateAndReverseResolve(_, skipValidateAndResolve))
  }

  private def toDisplayableProcessDetailsWithoutValidation(
      canonicalProcessDetails: BaseProcessDetails[CanonicalProcess]
  ): ValidatedProcessDetails = {
    canonicalProcessDetails.mapProcess { canonical =>
      val displayableProcess = ProcessConverter.toDisplayable(
        canonical,
        canonicalProcessDetails.processingType,
        canonicalProcessDetails.processCategory
      )
      ValidatedDisplayableProcess.withEmptyValidationResult(displayableProcess)
    }
  }

  private def enrichDetailsWithProcessState[PS: ProcessShapeFetchStrategy](
      process: BaseProcessDetails[PS]
  )(implicit user: LoggedUser, freshnessPolicy: DataFreshnessPolicy): Future[BaseProcessDetails[PS]] = {
    if (process.isFragment)
      Future.successful(process)
    else
      deploymentService
        .getProcessState(process)
        .map(state => process.copy(state = Some(state)))
  }

  private def validateAndReverseResolve(
      processDetails: BaseProcessDetails[CanonicalProcess],
      skipValidateAndResolve: Boolean
  ): ValidatedProcessDetails = {
    if (skipValidateAndResolve) {
      toDisplayableProcessDetailsWithoutValidation(processDetails)
    } else {
      validateAndReverseResolve(processDetails)
    }
  }

  private def validateAndReverseResolve(
      processDetails: BaseProcessDetails[CanonicalProcess]
  ): ValidatedProcessDetails = {
    processDetails.mapProcess { canonical: CanonicalProcess =>
      val processingType = processDetails.processingType
      val validationResult =
        processResolving.validateBeforeUiReverseResolving(canonical, processingType, processDetails.processCategory)
      processResolving.reverseResolveExpressions(
        canonical,
        processingType,
        processDetails.processCategory,
        validationResult
      )
    }
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
    getProcessDetailsOnly(processIdWithName).flatMap { process =>
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
    withArchivedProcess(processIdWithName, "Can't delete not archived scenario.") { _ =>
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

  // TODO: It's temporary solution to return Set[FragmentDetails], in future we should replace it by Set[BaseProcessDetails[PS]]
  override def getFragmentsDetails(
      processingTypes: Option[List[ProcessingType]]
  )(implicit user: LoggedUser): Future[Set[FragmentDetails]] = {
    fetchingProcessRepository
      .fetchProcessesDetails[CanonicalProcess](
        FetchProcessesDetailsQuery(isFragment = Some(true), isArchived = Some(false), processingTypes = processingTypes)
      )
      .map(_.map(sub => FragmentDetails(sub.json, sub.processCategory)).toSet)
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

  private def doOnProcessStateVerification[T](process: BaseProcessDetails[_], actionToCheck: ProcessActionType)(
      callback: => Future[T]
  )(implicit user: LoggedUser): Future[T] = {
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    deploymentService
      .getProcessState(process)
      .flatMap(state => {
        if (state.allowedActions.contains(actionToCheck)) {
          callback
        } else {
          throw ProcessIllegalAction(actionToCheck, process.idWithName, state)
        }
      })
  }

  private def doArchive(process: BaseProcessDetails[_])(implicit user: LoggedUser): Future[Unit] =
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
      callback: BaseProcessDetails[_] => Future[T]
  )(implicit user: LoggedUser): Future[T] = {
    getProcessDetailsOnly(processIdWithName).flatMap { process =>
      if (process.isArchived) {
        callback(process)
      } else {
        throw ProcessIllegalAction(errorMessage)
      }
    }
  }

  private def withNotArchivedProcess[T](processIdWithName: ProcessIdWithName, errorMessage: String)(
      callback: BaseProcessDetails[_] => Future[T]
  )(implicit user: LoggedUser): Future[T] = {
    getProcessDetailsOnly(processIdWithName).flatMap { process =>
      if (process.isArchived) {
        throw ProcessIllegalAction(errorMessage)
      } else {
        callback(process)
      }
    }
  }

  private def withNotArchivedProcess[T](processIdWithName: ProcessIdWithName, action: ProcessActionType)(
      callback: BaseProcessDetails[_] => Future[T]
  )(implicit user: LoggedUser): Future[T] =
    getProcessDetailsOnly(processIdWithName).flatMap { process =>
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
