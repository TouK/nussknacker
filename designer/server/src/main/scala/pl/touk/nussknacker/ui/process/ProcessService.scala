package pl.touk.nussknacker.ui.process

import cats.data.EitherT
import cats.implicits.toTraverseOps
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
import pl.touk.nussknacker.ui.EspError
import pl.touk.nussknacker.ui.EspError.XError
import pl.touk.nussknacker.ui.api.ProcessesQuery
import pl.touk.nussknacker.ui.api.ProcessesResources.UnmarshallError
import pl.touk.nussknacker.ui.process.ProcessService.{CreateProcessCommand, EmptyResponse, UpdateProcessCommand}
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
  type EmptyResponse = XError[Unit]

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
  def getProcessId(processName: ProcessName)(implicit ec: ExecutionContext): Future[XError[ProcessId]]

  def getProcess[PS: ProcessShapeFetchStrategy](processIdWithName: ProcessIdWithName)(
      implicit user: LoggedUser
  ): Future[XError[BaseProcessDetails[PS]]]

  def archiveProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[EmptyResponse]

  def deleteProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[EmptyResponse]

  def unArchiveProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[EmptyResponse]

  def renameProcess(processIdWithName: ProcessIdWithName, name: ProcessName)(
      implicit user: LoggedUser
  ): Future[XError[UpdateProcessNameResponse]]

  def updateCategory(processIdWithName: ProcessIdWithName, category: String)(
      implicit user: LoggedUser
  ): Future[XError[UpdateProcessCategoryResponse]]

  def createProcess(command: CreateProcessCommand)(implicit user: LoggedUser): Future[XError[ProcessResponse]]

  def updateProcess(processIdWithName: ProcessIdWithName, action: UpdateProcessCommand)(
      implicit user: LoggedUser
  ): Future[XError[UpdateProcessResponse]]

  def getProcesses[PS: ProcessShapeFetchStrategy](implicit user: LoggedUser): Future[List[BaseProcessDetails[PS]]]

  def getProcessesAndFragments[PS: ProcessShapeFetchStrategy](
      implicit user: LoggedUser
  ): Future[List[BaseProcessDetails[PS]]]

  def getArchivedProcessesAndFragments[PS: ProcessShapeFetchStrategy](
      implicit user: LoggedUser
  ): Future[List[BaseProcessDetails[PS]]]

  def getFragmentsDetails(processingTypes: Option[List[ProcessingType]])(
      implicit user: LoggedUser
  ): Future[Set[FragmentDetails]]

  def importProcess(processId: ProcessIdWithName, processData: String)(
      implicit user: LoggedUser
  ): Future[XError[ValidatedDisplayableProcess]]

  def getProcessActions(id: ProcessId): Future[List[ProcessAction]]

  def getProcesses(query: ProcessesQuery)(implicit user: LoggedUser): Future[List[BaseProcessDetails[_]]]

  def getProcessDetails(processId: ProcessIdWithName, skipValidateAndResolve: Boolean)(
      implicit user: LoggedUser
  ): Future[ValidatedProcessDetails]

  def getProcessDetails(processId: ProcessIdWithName, versionId: VersionId, skipValidateAndResolve: Boolean)(
      implicit user: LoggedUser
  ): Future[ValidatedProcessDetails]

  def getRawProcessDetails(processId: ProcessIdWithName, versionId: VersionId)(
      implicit user: LoggedUser
  ): Future[BaseProcessDetails[DisplayableProcess]]

  def getProcessesDetails(query: ProcessesQuery, skipValidateAndResolve: Boolean)(
      implicit user: LoggedUser
  ): Future[List[ValidatedProcessDetails]]

}

/**
 * ProcessService provides functionality for archive, unarchive, deploy, cancel process.
 * Each action includes verification based on actual process state and checking process is fragment / archived.
 */
class DBProcessService(
    deploymentService: DeploymentService,
    newProcessPreparer: NewProcessPreparer,
    processCategoryService: ProcessCategoryService,
    processResolving: UIProcessResolving,
    dbioRunner: DBIOActionRunner,
    fetchingProcessRepository: FetchingProcessRepository[Future],
    processActionRepository: ProcessActionRepository[DB],
    processRepository: ProcessRepository[DB],
    processValidation: ProcessValidation
)(implicit ec: ExecutionContext)
    extends ProcessService
    with LazyLogging {

  import cats.instances.future._
  import cats.syntax.either._

  override def archiveProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[EmptyResponse] =
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
  ): Future[XError[UpdateProcessNameResponse]] =
    withNotArchivedProcess(processIdWithName, "Can't rename archived scenario.") { process =>
      if (process.isFragment) {
        doRename(processIdWithName, name)
      } else {
        doOnProcessStateVerification(process, ProcessActionType.Rename)(doRename(processIdWithName, name))
      }
    }

  override def unArchiveProcess(
      processIdWithName: ProcessIdWithName
  )(implicit user: LoggedUser): Future[EmptyResponse] =
    withProcess(processIdWithName) { process =>
      if (process.isArchived) {
        dbioRunner
          .runInTransaction(
            DBIOAction.seq(
              processRepository.archive(processId = process.idWithName.id, isArchived = false),
              processActionRepository
                .markProcessAsUnArchived(processId = process.idWithName.id, process.processVersionId)
            )
          )
          .map(_ => ().asRight)
      } else {
        Future(Left(ProcessIllegalAction("Can't unarchive not archived scenario.")))
      }
    }

  override def deleteProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[EmptyResponse] =
    withArchivedProcess(processIdWithName, "Can't delete not archived scenario.") { process =>
      dbioRunner
        .runInTransaction(
          processRepository.deleteProcess(processIdWithName.id)
        )
        .map(_ => ().asRight)
    }

  override def updateCategory(processIdWithName: ProcessIdWithName, category: String)(
      implicit user: LoggedUser
  ): Future[XError[UpdateProcessCategoryResponse]] =
    withNotArchivedProcess(processIdWithName, "Can't update category archived scenario.") { process =>
      withProcessingType(category) { _ =>
        dbioRunner.runInTransaction(
          processRepository
            .updateCategory(processIdWithName.id, category)
            .map {
              case Right(_)    => Right(UpdateProcessCategoryResponse(process.processCategory, category))
              case Left(value) => Left(value)
            }
        )
      }
    }

  // FIXME: Create process should create two inserts (process, processVersion) in transactional way, but right we do all in process repository..
  override def createProcess(
      command: CreateProcessCommand
  )(implicit user: LoggedUser): Future[XError[ProcessResponse]] =
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
        Future.successful(Left(ProcessValidationError(propertiesErrors.map(_.message).mkString(", "))))
      } else {
        dbioRunner
          .runInTransaction(processRepository.saveNewProcess(action))
          .map {
            case Right(maybemaybeCreated) =>
              maybemaybeCreated
                .map(created => Right(toProcessResponse(command.processName, created)))
                .getOrElse(Left(ProcessValidationError("Unknown error on creating scenario.")))
            case Left(value) =>
              Left(value)
          }
      }
    }

  // FIXME: Update process should update process and create process version in transactional way, but right we do all in process repository..
  override def updateProcess(processIdWithName: ProcessIdWithName, action: UpdateProcessCommand)(
      implicit user: LoggedUser
  ): Future[XError[UpdateProcessResponse]] =
    withNotArchivedProcess(processIdWithName, "Can't update graph archived scenario.") { process =>
      val result = for {
        validation <- EitherT.fromEither[Future](
          FatalValidationError.saveNotAllowedAsError(
            processResolving.validateBeforeUiResolving(action.process)
          )
        )
        substituted = {
          processResolving.resolveExpressions(action.process, validation.typingInfo)
        }
        updateProcessAction = UpdateProcessAction(
          processIdWithName.id,
          substituted,
          Option(action.comment),
          increaseVersionWhenJsonNotChanged = false,
          forwardedUserName = action.forwardedUserName
        )
        processUpdated <- EitherT(dbioRunner.runInTransaction(processRepository.updateProcess(updateProcessAction)))
      } yield UpdateProcessResponse(
        processUpdated.newVersion
          .map(ProcessCreated(processIdWithName.id, _))
          .map(toProcessResponse(processIdWithName.name, _)),
        validation
      )

      result.value
    }

  override def getProcess[PS: ProcessShapeFetchStrategy](
      processIdWithName: ProcessIdWithName
  )(implicit user: LoggedUser): Future[XError[BaseProcessDetails[PS]]] =
    fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[PS](processIdWithName.id).flatMap {
      case Some(process) =>
        Future(Right(process))
      case None =>
        Future(Left(ProcessNotFoundError(processIdWithName.id.value.toString)))
    }

  override def getProcessId(processName: ProcessName)(implicit ec: ExecutionContext): Future[XError[ProcessId]] = {
    fetchingProcessRepository.fetchProcessId(processName).flatMap {
      case Some(process) =>
        Future(Right(process))
      case None =>
        Future(Left(ProcessNotFoundError(processName.toString)))
    }
  }

  override def getProcesses[PS: ProcessShapeFetchStrategy](
      implicit user: LoggedUser
  ): Future[List[BaseProcessDetails[PS]]] =
    getProcesses(user, isFragment = Some(false), isArchived = Some(false))

  override def getProcessesAndFragments[PS: ProcessShapeFetchStrategy](
      implicit user: LoggedUser
  ): Future[List[BaseProcessDetails[PS]]] =
    getProcesses(user, isFragment = None, isArchived = Some(false))

  override def getArchivedProcessesAndFragments[PS: ProcessShapeFetchStrategy](
      implicit user: LoggedUser
  ): Future[List[BaseProcessDetails[PS]]] =
    getProcesses(user, isFragment = None, isArchived = Some(true))

  // TODO: It's temporary solution to return Set[FragmentDetails], in future we should replace it by Set[BaseProcessDetails[PS]]
  override def getFragmentsDetails(
      processingTypes: Option[List[ProcessingType]]
  )(implicit user: LoggedUser): Future[Set[FragmentDetails]] = {
    fetchingProcessRepository
      .fetchProcessesDetails[CanonicalProcess](
        FetchProcessesDetailsQuery(isFragment = Some(true), isArchived = Some(false), processingTypes = processingTypes)
      )
      .map(processes =>
        processes
          .map(sub => {
            FragmentDetails(sub.json, sub.processCategory)
          })
          .toSet
      )
  }

  def importProcess(processId: ProcessIdWithName, jsonString: String)(
      implicit user: LoggedUser
  ): Future[XError[ValidatedDisplayableProcess]] = {
    withNotArchivedProcess(processId, "Import is not allowed for archived process.") { process =>
      val result = ProcessMarshaller
        .fromJson(jsonString)
        .leftMap(UnmarshallError)
        .toEither
        .map { jsonCanonicalProcess =>
          val canonical   = jsonCanonicalProcess.withProcessId(processId.name)
          val displayable = ProcessConverter.toDisplayable(canonical, process.processingType, process.processCategory)
          val validationResult = processResolving.validateBeforeUiReverseResolving(
            canonical,
            displayable.processingType,
            process.processCategory
          )
          new ValidatedDisplayableProcess(displayable, validationResult)
        }

      Future.successful(result)
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
      callback: => Future[XError[T]]
  )(implicit user: LoggedUser): Future[XError[T]] = {
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    deploymentService
      .getProcessState(process)
      .flatMap(state => {
        if (state.allowedActions.contains(actionToCheck)) {
          callback
        } else {
          Future(Left(ProcessIllegalAction(actionToCheck, process.idWithName, state)))
        }
      })
  }

  private def doArchive(process: BaseProcessDetails[_])(implicit user: LoggedUser): Future[EmptyResponse] =
    dbioRunner
      .runInTransaction(
        DBIOAction.seq(
          processRepository.archive(processId = process.idWithName.id, isArchived = true),
          processActionRepository.markProcessAsArchived(processId = process.idWithName.id, process.processVersionId)
        )
      )
      .map(_ => ().asRight)

  private def doRename(processIdWithName: ProcessIdWithName, name: ProcessName)(implicit user: LoggedUser) = {
    dbioRunner.runInTransaction(
      processRepository
        .renameProcess(processIdWithName, name)
        .map {
          case Right(_)    => Right(UpdateProcessNameResponse.create(processIdWithName.name.value, name.value))
          case Left(value) => Left(value)
        }
    )
  }

  override def getProcessActions(id: ProcessId): Future[List[ProcessAction]] = {
    dbioRunner.runInTransaction(processActionRepository.getFinishedProcessActions(id, None))
  }

  private def toProcessResponse(processName: ProcessName, created: ProcessCreated): ProcessResponse =
    ProcessResponse(created.processId, created.processVersionId, processName)

  private def withProcess[T](
      processIdWithName: ProcessIdWithName
  )(callback: BaseProcessDetails[_] => Future[XError[T]])(implicit user: LoggedUser) = {
    getProcess[Unit](processIdWithName).flatMap {
      case Left(err) => Future(Left(err))
      case Right(t)  => callback(t)
    }
  }

  private def withArchivedProcess[T](processIdWithName: ProcessIdWithName, errorMessage: String)(
      callback: BaseProcessDetails[_] => Future[XError[T]]
  )(implicit user: LoggedUser): Future[XError[T]] = {
    withProcess(processIdWithName) { process =>
      if (process.isArchived) {
        callback(process)
      } else {
        Future(Left(ProcessIllegalAction(errorMessage)))
      }
    }
  }

  private def withNotArchivedProcess[T](processIdWithName: ProcessIdWithName, errorMessage: String)(
      callback: BaseProcessDetails[_] => Future[XError[T]]
  )(implicit user: LoggedUser): Future[XError[T]] = {
    withProcess(processIdWithName) { process =>
      if (process.isArchived) {
        Future(Left(ProcessIllegalAction(errorMessage)))
      } else {
        callback(process)
      }
    }
  }

  private def withNotArchivedProcess[T](processIdWithName: ProcessIdWithName, action: ProcessActionType)(
      callback: BaseProcessDetails[_] => Future[XError[T]]
  )(implicit user: LoggedUser): Future[XError[T]] =
    withProcess(processIdWithName) { process =>
      if (process.isArchived) {
        Future(Left(ProcessIllegalAction.archived(action, process.idWithName)))
      } else {
        callback(process)
      }
    }

  private def withProcessingType[T](
      category: String
  )(callback: ProcessingType => Future[Either[EspError, T]]): Future[Either[EspError, T]] =
    processCategoryService.getTypeForCategory(category) match {
      case Some(processingType) =>
        callback(processingType)
      case None =>
        Future(Left(ProcessValidationError("Scenario category not found.")))
    }

  private def getProcesses[PS: ProcessShapeFetchStrategy](
      user: LoggedUser,
      isFragment: Option[Boolean],
      isArchived: Option[Boolean]
  ): Future[List[BaseProcessDetails[PS]]] = {
    val userCategories = processCategoryService.getUserCategories(user)
    val shapeStrategy  = implicitly[ProcessShapeFetchStrategy[PS]]
    fetchingProcessRepository.fetchProcessesDetails(
      FetchProcessesDetailsQuery(isFragment = isFragment, isArchived = isArchived, categories = Some(userCategories))
    )(shapeStrategy, user, ec)
  }

  override def getProcesses(query: ProcessesQuery)(implicit user: LoggedUser): Future[List[BaseProcessDetails[_]]] = {
    // To not overload engine, for list of processes we provide statuses that can be cached
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.CanBeCached
    fetchingProcessRepository
      .fetchProcessesDetails[Unit](query.toRepositoryQuery)
      .flatMap(deploymentService.enrichDetailsWithProcessState)
  }

  override def getProcessDetails(processId: ProcessIdWithName, skipValidateAndResolve: Boolean)(
      implicit user: LoggedUser
  ): Future[ValidatedProcessDetails] = {
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    fetchingProcessRepository
      .fetchLatestProcessDetailsForProcessId[CanonicalProcess](processId.id)
      .flatMap(enrichWithStateAndThenValidateAndReverseResolve(processId, _, skipValidateAndResolve))
  }

  override def getProcessDetails(processId: ProcessIdWithName, versionId: VersionId, skipValidateAndResolve: Boolean)(
      implicit user: LoggedUser
  ): Future[ValidatedProcessDetails] = {
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    fetchingProcessRepository
      .fetchProcessDetailsForId[CanonicalProcess](processId.id, versionId)
      .flatMap(enrichWithStateAndThenValidateAndReverseResolve(processId, _, skipValidateAndResolve))
  }

  def getRawProcessDetails(processId: ProcessIdWithName, versionId: VersionId)(
      implicit user: LoggedUser
  ): Future[BaseProcessDetails[DisplayableProcess]] = {
    fetchingProcessRepository
      .fetchProcessDetailsForId[DisplayableProcess](processId.id, versionId)
      .map(_.getOrElse(throw ProcessVersionNotFoundError(processId.id, versionId)))
  }

  override def getProcessesDetails(query: ProcessesQuery, skipValidateAndResolve: Boolean)(
      implicit user: LoggedUser
  ): Future[List[ValidatedProcessDetails]] = {
    fetchingProcessRepository.fetchProcessesDetails[CanonicalProcess](query.toRepositoryQuery).map { processes =>
      // In contrary method version with processId, here we skip enrichDetailsWithProcessState stage. Is it ok?
      processes.map(validateAndReverseResolve(_, skipValidateAndResolve))
    }
  }

  private def enrichWithStateAndThenValidateAndReverseResolve(
      processId: ProcessIdWithName,
      canonicalProcessOpt: Option[BaseProcessDetails[CanonicalProcess]],
      skipValidateAndResolve: Boolean
  )(implicit user: LoggedUser, freshnessPolicy: DataFreshnessPolicy) = {
    canonicalProcessOpt
      .map(enrichDetailsWithProcessState)
      .sequence
      .map(
        _.map(validateAndReverseResolve(_, skipValidateAndResolve))
          .getOrElse(throw ProcessNotFoundError(processId.id.toString))
      )
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
      new ValidatedDisplayableProcess(displayableProcess, ValidationResult.success)
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
  ) = {
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

}
