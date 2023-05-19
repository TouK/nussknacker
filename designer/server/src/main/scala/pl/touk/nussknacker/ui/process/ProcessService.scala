package pl.touk.nussknacker.ui.process

import cats.data.EitherT
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances.DB
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.{DataFreshnessPolicy, ProcessActionType, ProcessState}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.process._
import pl.touk.nussknacker.restmodel.processdetails.{BaseProcessDetails, ProcessShapeFetchStrategy}
import pl.touk.nussknacker.ui.EspError
import pl.touk.nussknacker.ui.EspError.XError
import pl.touk.nussknacker.ui.api.ProcessesResources.UnmarshallError
import pl.touk.nussknacker.ui.component.ComponentsUsageHelper
import pl.touk.nussknacker.ui.process.ProcessService.{CreateProcessCommand, EmptyResponse, UpdateProcessCommand}
import pl.touk.nussknacker.ui.process.deployment.DeploymentService
import pl.touk.nussknacker.ui.process.exception.{ProcessIllegalAction, ProcessValidationError}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository.FetchProcessesDetailsQuery
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.{CreateProcessAction, ProcessCreated, UpdateProcessAction}
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.process.subprocess.SubprocessDetails
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolving
import pl.touk.nussknacker.ui.validation.{FatalValidationError, ProcessValidation}
import slick.dbio.DBIOAction

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds


object ProcessService {
  type EmptyResponse = XError[Unit]

  @JsonCodec case class CreateProcessCommand(processName: ProcessName, category: String, isSubprocess: Boolean)

  @JsonCodec case class UpdateProcessCommand(process: DisplayableProcess, comment: UpdateProcessComment)
}

trait ProcessService {

  def getProcess[PS: ProcessShapeFetchStrategy](processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[XError[BaseProcessDetails[PS]]]

  def archiveProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[EmptyResponse]

  def deleteProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[EmptyResponse]

  def unArchiveProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[EmptyResponse]

  def renameProcess(processIdWithName: ProcessIdWithName, name: ProcessName)(implicit user: LoggedUser): Future[XError[UpdateProcessNameResponse]]

  def updateCategory(processIdWithName: ProcessIdWithName, category: String)(implicit user: LoggedUser): Future[XError[UpdateProcessCategoryResponse]]

  def createProcess(command: CreateProcessCommand)(implicit user: LoggedUser): Future[XError[ProcessResponse]]

  def updateProcess(processIdWithName: ProcessIdWithName, action: UpdateProcessCommand)(implicit user: LoggedUser): Future[XError[UpdateProcessResponse]]

  def getProcesses[PS: ProcessShapeFetchStrategy](implicit user: LoggedUser): Future[List[BaseProcessDetails[PS]]]

  def getArchivedProcesses[PS: ProcessShapeFetchStrategy](implicit user: LoggedUser): Future[List[BaseProcessDetails[PS]]]

  def getSubProcesses(processingTypes: Option[List[ProcessingType]])(implicit user: LoggedUser): Future[Set[SubprocessDetails]]

  def importProcess(processId: ProcessIdWithName, processData: String)(implicit user: LoggedUser): Future[XError[ValidatedDisplayableProcess]]
}

/**
  * ProcessService provides functionality for archive, unarchive, deploy, cancel process.
  * Each action includes verification based on actual process state and checking process is subprocess / archived.
  */
class DBProcessService(deploymentService: DeploymentService,
                       newProcessPreparer: NewProcessPreparer,
                       processCategoryService: ProcessCategoryService,
                       processResolving: UIProcessResolving,
                       dbioRunner: DBIOActionRunner,
                       fetchingProcessRepository: FetchingProcessRepository[Future],
                       processActionRepository: ProcessActionRepository[DB],
                       processRepository: ProcessRepository[DB],
                       processValidation: ProcessValidation)(implicit ec: ExecutionContext) extends ProcessService with LazyLogging {

  import cats.instances.future._
  import cats.syntax.either._

  override def archiveProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[EmptyResponse] =
    withNotArchivedProcess(processIdWithName, ProcessActionType.Archive) { process =>
      if (process.isSubprocess) {
        archiveSubprocess(process)
      } else {
        // FIXME: This doesn't work correctly because concurrent request can change a state and double archive actions will be done.
        //        See ManagementResourcesConcurrentSpec and how DeploymentService handles it correctly for deploy and cancel
        doOnProcessStateVerification(process, ProcessActionType.Archive)(doArchive)
      }
    }

  override def unArchiveProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[EmptyResponse] =
    withProcess(processIdWithName) { process =>
      if (process.isArchived) {
        dbioRunner.runInTransaction(DBIOAction.seq(
          processRepository.archive(processId = process.idWithName.id, isArchived = false),
          processActionRepository.markProcessAsUnArchived(processId = process.idWithName.id, process.processVersionId)
        )).map(_ => ().asRight)
      } else {
        Future(Left(ProcessIllegalAction("Can't unarchive not archived scenario.")))
      }
    }


  // FIXME: How should look flow? Process -> archive -> delete?
  override def deleteProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[EmptyResponse] =
    withProcess(processIdWithName) { process =>
      withNotRunningState(process, "Can't delete still running scenario.") { _ =>
        dbioRunner.runInTransaction(
          processRepository.deleteProcess(processIdWithName.id)
        ).map(_ => ().asRight)
      }
    }

  override def renameProcess(processIdWithName: ProcessIdWithName, name: ProcessName)(implicit user: LoggedUser): Future[XError[UpdateProcessNameResponse]] =
    withNotArchivedProcess(processIdWithName, "Can't rename archived scenario.") { process =>
      withNotRunningState(process, "Can't change name still running scenario.") { _ =>
        dbioRunner.runInTransaction(
          processRepository
            .renameProcess(processIdWithName, name)
            .map {
              case Right(_) => Right(UpdateProcessNameResponse.create(process.name, name.value))
              case Left(value) => Left(value)
            }
        )
      }
    }

  override def updateCategory(processIdWithName: ProcessIdWithName, category: String)(implicit user: LoggedUser): Future[XError[UpdateProcessCategoryResponse]] =
    withNotArchivedProcess(processIdWithName, "Can't update category archived scenario.") { process =>
      withProcessingType(category) { _ =>
        dbioRunner.runInTransaction(
          processRepository
            .updateCategory(processIdWithName.id, category)
            .map {
              case Right(_) => Right(UpdateProcessCategoryResponse(process.processCategory, category))
              case Left(value) => Left(value)
            }
        )
      }
    }

  // FIXME: Create process should create two inserts (process, processVersion) in transactional way, but right we do all in process repository..
  override def createProcess(command: CreateProcessCommand)(implicit user: LoggedUser): Future[XError[ProcessResponse]] =
    withProcessingType(command.category) { processingType =>
      val emptyCanonicalProcess = newProcessPreparer.prepareEmptyProcess(command.processName.value, processingType, command.isSubprocess)
      val emptyComponentsUsages = ScenarioComponentsUsages.Empty
      val action = CreateProcessAction(command.processName, command.category, emptyCanonicalProcess, processingType, command.isSubprocess, emptyComponentsUsages)

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
  override def updateProcess(processIdWithName: ProcessIdWithName, action: UpdateProcessCommand)(implicit user: LoggedUser): Future[XError[UpdateProcessResponse]] =
    withNotArchivedProcess(processIdWithName, "Can't update graph archived scenario.") { process =>
      val result = for {
        validation <- EitherT.fromEither[Future](FatalValidationError.saveNotAllowedAsError(
          processResolving.validateBeforeUiResolving(action.process)
        ))
        substituted = {
          processResolving.resolveExpressions(action.process, validation.typingInfo)
        }
        componentsUsages = ComponentsUsageHelper.computeScenarioUsages(substituted)
        processUpdated <- EitherT(dbioRunner
          .runInTransaction(processRepository
            .updateProcess(UpdateProcessAction(processIdWithName.id, substituted, componentsUsages, Option(action.comment), increaseVersionWhenJsonNotChanged = false))
          ))
      } yield UpdateProcessResponse(
        processUpdated
          .newVersion
          .map(ProcessCreated(processIdWithName.id, _))
          .map(toProcessResponse(processIdWithName.name, _)),
        validation
      )

      result.value
    }

  override def getProcess[PS: ProcessShapeFetchStrategy](processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[XError[BaseProcessDetails[PS]]] =
    fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[PS](processIdWithName.id).flatMap {
      case Some(process) =>
        Future(Right(process))
      case None =>
        Future(Left(ProcessNotFoundError(processIdWithName.id.value.toString)))
    }

  override def getProcesses[PS: ProcessShapeFetchStrategy](implicit user: LoggedUser): Future[List[BaseProcessDetails[PS]]] =
    getProcesses(user, isArchived = false)

  override def getArchivedProcesses[PS: ProcessShapeFetchStrategy](implicit user: LoggedUser): Future[List[BaseProcessDetails[PS]]] =
    getProcesses(user, isArchived = true)

  //TODO: It's temporary solution to return Set[SubprocessDetails], in future we should replace it by Set[BaseProcessDetails[PS]]
  override def getSubProcesses(processingTypes: Option[List[ProcessingType]])(implicit user: LoggedUser): Future[Set[SubprocessDetails]] = {
    fetchingProcessRepository
      .fetchProcessesDetails[CanonicalProcess](FetchProcessesDetailsQuery(isSubprocess = Some(true), isArchived = Some(false), processingTypes = processingTypes))
      .map(processes => processes.map(sub => {
        SubprocessDetails(sub.json, sub.processCategory)
      }).toSet)
  }

  def importProcess(processId: ProcessIdWithName, jsonString: String)(implicit user: LoggedUser): Future[XError[ValidatedDisplayableProcess]] = {
    withNotArchivedProcess(processId, "Import is not allowed for archived process.") { process =>
      val result = ProcessMarshaller.fromJson(jsonString).leftMap(UnmarshallError).toEither
        .map{ jsonCanonicalProcess =>
          val canonical = jsonCanonicalProcess.withProcessId(processId.name)
          val displayable = ProcessConverter.toDisplayable(canonical, process.processingType, process.processCategory)
          val validationResult = processResolving.validateBeforeUiReverseResolving(canonical, displayable.processingType, process.processCategory)
          new ValidatedDisplayableProcess(displayable, validationResult)
        }

      Future.successful(result)
    }
  }

  private def validateInitialScenarioProperties(canonicalProcess: CanonicalProcess, processingType:ProcessingType, category: String) = {
    val validationResult = processValidation.processingTypeValidationWithTypingInfo(canonicalProcess, processingType, category)
    validationResult.errors.processPropertiesErrors

  }

  private def archiveSubprocess(process: BaseProcessDetails[_])(implicit user: LoggedUser): Future[EmptyResponse] =
    doArchive(process)

  private def doOnProcessStateVerification(process: BaseProcessDetails[_], actionToCheck: ProcessActionType)(callback: BaseProcessDetails[_] => Future[EmptyResponse])
                                          (implicit user: LoggedUser): Future[EmptyResponse] = {
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    deploymentService.getProcessState(process).flatMap(state => {
      if (state.allowedActions.contains(actionToCheck)) {
        callback(process)
      } else {
        Future(Left(ProcessIllegalAction(actionToCheck, process.idWithName, state)))
      }
    })
  }

  private def doArchive(process: BaseProcessDetails[_])(implicit user: LoggedUser): Future[EmptyResponse] =
    dbioRunner.runInTransaction(DBIOAction.seq(
      processRepository.archive(processId = process.idWithName.id, isArchived = true),
      processActionRepository.markProcessAsArchived(processId = process.idWithName.id, process.processVersionId)
    )).map(_ => ().asRight)

  private def toProcessResponse(processName: ProcessName, created: ProcessCreated): ProcessResponse =
    ProcessResponse(created.processId, created.processVersionId, processName)

  private def withProcess[T](processIdWithName: ProcessIdWithName)(callback: BaseProcessDetails[_] => Future[XError[T]])(implicit user: LoggedUser) = {
    getProcess[Unit](processIdWithName).flatMap {
      case Left(err) => Future(Left(err))
      case Right(t) => callback(t)
    }
  }

  private def withNotArchivedProcess[T](processIdWithName: ProcessIdWithName, errorMessage: String)(callback: BaseProcessDetails[_] => Future[XError[T]])(implicit user: LoggedUser): Future[XError[T]] = {
    withProcess(processIdWithName) { process =>
      if (process.isArchived) {
        Future(Left(ProcessIllegalAction(errorMessage)))
      } else {
        callback(process)
      }
    }
  }

  private def withNotArchivedProcess[T](processIdWithName: ProcessIdWithName, action: ProcessActionType)(callback: BaseProcessDetails[_] => Future[XError[T]])(implicit user: LoggedUser): Future[XError[T]] =
    withProcess(processIdWithName) { process =>
      if (process.isArchived) {
        Future(Left(ProcessIllegalAction.archived(action, process.idWithName)))
      } else {
        callback(process)
      }
    }

  private def withNotRunningState[T](process: BaseProcessDetails[_], errorMessage: String)(callback: ProcessState => Future[XError[T]])(implicit user: LoggedUser) = {
    if (process.isDeployed) {
      Future(Left(ProcessIllegalAction(errorMessage)))
    } else {
      implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
      deploymentService.getProcessState(process).flatMap(ps => {
        if (ps.status.isRunning) {
          Future(Left(ProcessIllegalAction(errorMessage)))
        } else {
          callback(ps)
        }
      })
    }
  }

  private def withProcessingType[T](category: String)(callback: ProcessingType => Future[Either[EspError, T]]): Future[Either[EspError, T]] =
    processCategoryService.getTypeForCategory(category) match {
      case Some(processingType) =>
        callback(processingType)
      case None =>
        Future(Left(ProcessValidationError("Scenario category not found.")))
    }

  private def getProcesses[PS: ProcessShapeFetchStrategy](user: LoggedUser, isArchived: Boolean): Future[List[BaseProcessDetails[PS]]] = {
    val userCategories = processCategoryService.getUserCategories(user)
    val shapeStrategy = implicitly[ProcessShapeFetchStrategy[PS]]
    fetchingProcessRepository.fetchProcessesDetails(FetchProcessesDetailsQuery(isArchived = Some(isArchived), categories = Some(userCategories)))(shapeStrategy, user, ec)
  }

}
