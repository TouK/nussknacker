package pl.touk.nussknacker.ui.process

import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.{GraphProcess, ProcessActionType, ProcessState}
import pl.touk.nussknacker.restmodel.process.{ProcessId, ProcessIdWithName, ProcessVersionId}
import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails
import pl.touk.nussknacker.ui.EspError.XError
import pl.touk.nussknacker.ui.process.deployment.{Cancel, CheckStatus, Deploy}
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.process.repository.{FetchingProcessRepository, ProcessActionRepository, ProcessRepository, RepositoryManager}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.ui.process.exception.{ProcessIllegalAction, ProcessValidationError}
import akka.actor.ActorRef
import akka.pattern.ask
import cats.data.EitherT
import db.util.DBIOActionInstances.DB
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.{CreateProcessAction, UpdateProcessAction}

import java.time
import scala.language.higherKinds
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.restmodel.ProcessType
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.EspError
import pl.touk.nussknacker.ui.process.ProcessService.{CreateProcessCommand, EmptyResponse, UpdateProcessCommand}
import pl.touk.nussknacker.restmodel.process._
import pl.touk.nussknacker.ui.db.entity.ProcessVersionEntityData
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolving
import pl.touk.nussknacker.ui.validation.FatalValidationError


object ProcessService {
  type EmptyResponse = XError[Unit]

  @JsonCodec case class CreateProcessCommand(processName: ProcessName, category: String, isSubprocess: Boolean)

  @JsonCodec case class UpdateProcessCommand(process: DisplayableProcess, comment: String)
}

trait ProcessService {

  def getProcessState(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[ProcessState]

  def archiveProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[EmptyResponse]

  def deleteProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[EmptyResponse]

  def unArchiveProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[EmptyResponse]

  def deployProcess(processIdWithName: ProcessIdWithName, savepointPath: Option[String], comment: Option[String])(implicit user: LoggedUser): Future[EmptyResponse]

  def cancelProcess(processIdWithName: ProcessIdWithName, comment: Option[String])(implicit user: LoggedUser): Future[EmptyResponse]

  def renameProcess(processIdWithName: ProcessIdWithName, name: String)(implicit user: LoggedUser): Future[XError[UpdateProcessNameResponse]]

  def updateCategory(processIdWithName: ProcessIdWithName, category: String)(implicit user: LoggedUser): Future[XError[UpdateProcessCategoryResponse]]

  def createProcess(command: CreateProcessCommand)(implicit user: LoggedUser): Future[XError[ProcessResponse]]

  def updateProcess(processIdWithName: ProcessIdWithName, action: UpdateProcessCommand)(implicit user: LoggedUser): Future[XError[UpdateProcessResponse]]

}

/**
  * ProcessService provides functionality for archive, unarchive, deploy, cancel process.
  * Each action includes verification based on actual process state and checking process is subprocess / archived.
  */
class DBProcessService(managerActor: ActorRef,
                       requestTimeLimit: time.Duration,
                       newProcessPreparer: NewProcessPreparer,
                       typesForCategories: ProcessTypesForCategories,
                       processResolving: UIProcessResolving,
                       repositoryManager: RepositoryManager[DB],
                       fetchingProcessRepository: FetchingProcessRepository[Future],
                       processActionRepository: ProcessActionRepository[DB],
                       processRepository: ProcessRepository[DB])(implicit ec: ExecutionContext) extends ProcessService with LazyLogging {

  import scala.concurrent.duration._
  import cats.instances.future._
  import cats.syntax.either._

  private implicit val timeout: Timeout = Timeout(requestTimeLimit.toMillis millis)

  /**
    * Handling error at retrieving status from manager is created at ManagementActor
    */
  override def getProcessState(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[ProcessState] =
    (managerActor ? CheckStatus(processIdWithName, user)).mapTo[ProcessState]

  override def archiveProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[EmptyResponse] =
    withNotArchivedProcess(processIdWithName, ProcessActionType.Archive) { process =>
      if (process.isSubprocess) {
        archiveSubprocess(process)
      } else {
        doOnProcessStateVerification(process, ProcessActionType.Archive)(doArchive)
      }
    }

  override def unArchiveProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[EmptyResponse] =
    withProcess(processIdWithName) { process =>
      if (process.isArchived) {
        repositoryManager.runInTransaction(
          processRepository.archive(processId = process.idWithName.id, isArchived = false),
          processActionRepository.markProcessAsUnArchived(processId = process.idWithName.id, process.processVersionId)
        ).map(_ => ().asRight)
      } else {
        Future(Left(ProcessIllegalAction("Can't unarchive not archived process.")))
      }
    }

  override def deployProcess(processIdWithName: ProcessIdWithName, savepointPath: Option[String], comment: Option[String])(implicit user: LoggedUser): Future[EmptyResponse] =
    doAction(ProcessActionType.Deploy, processIdWithName, savepointPath, comment) { (processIdWithName: ProcessIdWithName, savepointPath: Option[String], comment: Option[String]) =>
      (managerActor ? Deploy(processIdWithName, user, savepointPath, comment))
        .map(_ => ().asRight)
    }

  override def cancelProcess(processIdWithName: ProcessIdWithName, comment: Option[String])(implicit user: LoggedUser): Future[EmptyResponse] =
    doAction(ProcessActionType.Cancel, processIdWithName, None, comment) { (processIdWithName: ProcessIdWithName, _: Option[String], comment: Option[String]) =>
      (managerActor ? Cancel(processIdWithName, user, comment))
        .map(_ => ().asRight)
    }


  private def doAction(action: ProcessActionType, processIdWithName: ProcessIdWithName, savepointPath: Option[String], comment: Option[String])
                      (actionToDo: (ProcessIdWithName, Option[String], Option[String]) => Future[EmptyResponse])
                      (implicit ec: ExecutionContext, user: LoggedUser): Future[EmptyResponse] = {
    withNotArchivedProcess(processIdWithName, action) { process =>
      if (process.isSubprocess) {
        Future(Left(ProcessIllegalAction.subprocess(action, processIdWithName)))
      } else {
        getProcessState(processIdWithName).flatMap(ps => {
          if (ps.allowedActions.contains(action)) {
            actionToDo(processIdWithName, savepointPath, comment)
          } else {
            Future(Left(ProcessIllegalAction(action, processIdWithName, ps)))
          }
        })
      }
    }
  }

  // FIXME: How should look flow? Process -> archive -> delete?
  override def deleteProcess(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[EmptyResponse] =
    withProcess(processIdWithName) { process =>
      withNotRunningState(process, "Can't delete still running process.") { _ =>
        repositoryManager.run(
          processRepository.deleteProcess(processIdWithName.id)
        ).map(_ => ().asRight)
      }
    }

  override def renameProcess(processIdWithName: ProcessIdWithName, name: String)(implicit user: LoggedUser): Future[XError[UpdateProcessNameResponse]] =
    withNotArchivedProcess(processIdWithName, "Can't rename archived process.") { process =>
      withNotRunningState(process, "Can't change name still running process.") { _ =>
        repositoryManager.run(
          processRepository
            .renameProcess(processIdWithName.id, name)
            .map {
              case Right(_) => Right(UpdateProcessNameResponse.create(process.name, name))
              case Left(value) => Left(value)
            }
        )
      }
    }

  override def updateCategory(processIdWithName: ProcessIdWithName, category: String)(implicit user: LoggedUser): Future[XError[UpdateProcessCategoryResponse]] =
    withNotArchivedProcess(processIdWithName, "Can't update category archived process.") { process =>
      withProcessingType(category) { _ =>
        repositoryManager.run(
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
      val processDeploymentData = GraphProcess(ProcessMarshaller.toJson(emptyCanonicalProcess).noSpaces)
      val action = CreateProcessAction(command.processName, command.category, processDeploymentData, processingType, command.isSubprocess)

      repositoryManager
        .run(processRepository.saveNewProcess(action))
        .map{
          case Right(maybeEntity) =>
            maybeEntity
              .map(entity => Right(toProcessResponse(command.processName, entity)))
              .getOrElse(Left(ProcessValidationError("Unknown error on creating process.")))
          case Left(value) =>
            Left(value)
      }
    }

  // FIXME: Update process should update process and create process version in transactional way, but right we do all in process repository..
  override def updateProcess(processIdWithName: ProcessIdWithName, action: UpdateProcessCommand)(implicit user: LoggedUser): Future[XError[UpdateProcessResponse]] =
    withNotArchivedProcess(processIdWithName, "Can't update graph archived process.") { process =>
      withNotCustomProcess(process, "Can't update custom process.") { _ =>
        val result = for {
          validation <- EitherT.fromEither[Future](FatalValidationError.saveNotAllowedAsError(processResolving.validateBeforeUiResolving(action.process)))
          deploymentData = {
            val substituted = processResolving.resolveExpressions(action.process, validation.typingInfo)
            val json = ProcessMarshaller.toJson(substituted).noSpaces
            GraphProcess(json)
          }
          processVersionData <- EitherT(repositoryManager.run(processRepository.updateProcess(UpdateProcessAction(processIdWithName.id, deploymentData, action.comment))))
        } yield UpdateProcessResponse(
          processVersionData.map(toProcessResponse(processIdWithName.name, _)),
          validation
        )

        result.value
      }
    }

  private def archiveSubprocess(process: BaseProcessDetails[_])(implicit user: LoggedUser): Future[EmptyResponse] =
    doArchive(process)

  private def doOnProcessStateVerification(process: BaseProcessDetails[_], actionToCheck: ProcessActionType)(action: BaseProcessDetails[_] => Future[EmptyResponse])
                                          (implicit ec: ExecutionContext, user: LoggedUser): Future[EmptyResponse] =
    getProcessState(process.idWithName).flatMap(state => {
      if (state.allowedActions.contains(actionToCheck)) {
        action(process)
      } else {
        Future(Left(ProcessIllegalAction(actionToCheck, process.idWithName, state)))
      }
    })

  private def doArchive(process: BaseProcessDetails[_])(implicit user: LoggedUser): Future[EmptyResponse] =
    repositoryManager.runInTransaction(
      processRepository.archive(processId = process.idWithName.id, isArchived = true),
      processActionRepository.markProcessAsArchived(processId = process.idWithName.id, process.processVersionId)
    ).map(_ => ().asRight)

  private def toProcessResponse(processName: ProcessName, processVersionEntity: ProcessVersionEntityData): ProcessResponse =
    ProcessResponse(
      ProcessId(processVersionEntity.processId),
      ProcessVersionId(processVersionEntity.id),
      processName,
      processVersionEntity.json,
      processVersionEntity.createDate.getTime,
      processVersionEntity.user
    )

  private def withProcess[T](processIdWithName: ProcessIdWithName)(action: BaseProcessDetails[_] => Future[XError[T]])(implicit user: LoggedUser) = {
    fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](processIdWithName.id).flatMap {
      case Some(process) =>
        action(process)
      case None =>
        Future(Left(ProcessNotFoundError(processIdWithName.id.value.toString)))
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
      getProcessState(process.idWithName).flatMap(ps => {
        if (ps.status.isRunning) {
          Future(Left(ProcessIllegalAction(errorMessage)))
        } else {
          callback(ps)
        }
      })
    }
  }

  private def withProcessingType[T](category: String)(callback: ProcessingType => Future[Either[EspError, T]]): Future[Either[EspError, T]] =
    typesForCategories.getTypeForCategory(category) match {
      case Some(processingType) =>
        callback(processingType)
      case None =>
        Future(Left(ProcessValidationError("Process category not found.")))
    }

  private def withNotCustomProcess[T](process: BaseProcessDetails[_], errorMessage: String)(action: BaseProcessDetails[_] => Future[XError[T]])(implicit user: LoggedUser) = {
    if (process.processType == ProcessType.Custom) {
      Future(Left(ProcessIllegalAction(errorMessage)))
    } else {
      action(process)
    }
  }
}
