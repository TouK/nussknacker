package pl.touk.nussknacker.ui.process

import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.{ProcessActionType, ProcessState}
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails
import pl.touk.nussknacker.ui.EspError.XError
import pl.touk.nussknacker.ui.process.deployment.{Cancel, CheckStatus, Deploy}
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.process.repository.{FetchingProcessRepository, ProcessActionRepository, WriteProcessRepository}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.ui.process.exception.ProcessIllegalAction
import akka.actor.ActorRef
import akka.pattern.ask
import java.time

/**
  * ProcessService provides functionality for archive, unarchive, deploy, cancel process.
  * Each action includes verification based on actual process state and checking process is subprocess / archived.
  */
class ProcessService(managerActor: ActorRef,
                     requestTimeLimit: time.Duration,
                     processRepository: FetchingProcessRepository[Future],
                     processActionRepository: ProcessActionRepository,
                     writeRepository: WriteProcessRepository) extends LazyLogging {

  type EmptyResponse = XError[Unit]
  type DeployResponse = XError[Any]

  import scala.concurrent.duration._

  private implicit val timeout: Timeout = Timeout(requestTimeLimit.toMillis millis)

  /**
    * Handling error at retrieving status from manager is created at ManagementActor
    */
  def getProcessState(processIdWithName: ProcessIdWithName)(implicit ec: ExecutionContext, user: LoggedUser): Future[ProcessState] =
    (managerActor ? CheckStatus(processIdWithName, user)).mapTo[ProcessState]

  def archiveProcess(processIdWithName: ProcessIdWithName)(implicit ec: ExecutionContext, user: LoggedUser): Future[EmptyResponse] = {
    processRepository.fetchLatestProcessDetailsForProcessId[Unit](processIdWithName.id).flatMap {
      case Some(process) if process.isArchived =>
        Future(Left(ProcessIllegalAction("Can't archive already archived process.")))
      case Some(process) if process.isSubprocess =>
        archiveSubprocess(process)
      case Some(process) =>
        doOnProcessStateVerification(process, ProcessActionType.Archive)(doArchive)
      case None =>
        Future(Left(ProcessNotFoundError(processIdWithName.id.value.toString)))
    }
  }

  /**
    * FIXME: Add checking subprocess is used by any of already working process. ProcessResourcesSpec contains ignored test for it.
    */
  private def archiveSubprocess(process: BaseProcessDetails[_])(implicit ec: ExecutionContext, user: LoggedUser): Future[EmptyResponse] =
    doArchive(process)

  private def doOnProcessStateVerification(process: BaseProcessDetails[_], actionToCheck: ProcessActionType)
                                          (action: BaseProcessDetails[_] => Future[EmptyResponse])
                                          (implicit ec: ExecutionContext, user: LoggedUser): Future[EmptyResponse] =
    getProcessState(process.idWithName).flatMap(state => {
      if (state.allowedActions.contains(actionToCheck)) {
        action(process)
      } else {
        Future(Left(ProcessIllegalAction(actionToCheck, process.idWithName, state)))
      }
    })

  //FIXME: Right now we don't do it in transaction..
  private def doArchive(process: BaseProcessDetails[_])(implicit ec: ExecutionContext, user: LoggedUser): Future[EmptyResponse] =
    for {
      archive <- writeRepository.archive(processId = process.idWithName.id, isArchived = true)
      _ <- processActionRepository.markProcessAsArchived(process.idWithName.id, process.processVersionId, None)
    } yield archive

  def unArchiveProcess(processIdWithName: ProcessIdWithName)(implicit ec: ExecutionContext, user: LoggedUser): Future[EmptyResponse] = {
    processRepository.fetchLatestProcessDetailsForProcessId[Unit](processIdWithName.id).flatMap {
      case Some(process) if !process.isArchived =>
        Future(Left(ProcessIllegalAction("Can't unarchive not archived process.")))
      case Some(process) =>
        doUnArchive(process)
      case None =>
        Future(Left(ProcessNotFoundError(processIdWithName.id.value.toString)))
    }
  }

  //FIXME: Right now we don't do it in transaction..
  private def doUnArchive(process: BaseProcessDetails[_])(implicit ec: ExecutionContext, user: LoggedUser): Future[EmptyResponse] =
    for {
      archive <- writeRepository.archive(processId = process.idWithName.id, isArchived = false)
      _ <- processActionRepository.markProcessAsUnArchived(process.idWithName.id, process.processVersionId, None)
    } yield archive

   def deployProcess(processIdWithName: ProcessIdWithName, savepointPath: Option[String], comment: Option[String])(implicit ec: ExecutionContext, user: LoggedUser): Future[EmptyResponse] =
     doAction(ProcessActionType.Deploy, processIdWithName, savepointPath, comment){ (processIdWithName: ProcessIdWithName, savepointPath: Option[String], comment: Option[String]) =>
       (managerActor ? Deploy(processIdWithName, user, savepointPath, comment))
         .map(_ => Right(Unit))
     }

  def cancelProcess(processIdWithName: ProcessIdWithName, comment: Option[String])(implicit ec: ExecutionContext, user: LoggedUser): Future[EmptyResponse] =
   doAction(ProcessActionType.Cancel, processIdWithName, None, comment){ (processIdWithName: ProcessIdWithName, _: Option[String], comment: Option[String]) =>
     (managerActor ? Cancel(processIdWithName, user, comment))
       .map(_ => Right(Unit))
   }

  private def doAction(action: ProcessActionType, processIdWithName: ProcessIdWithName, savepointPath: Option[String], comment: Option[String])
                      (actionToDo: (ProcessIdWithName, Option[String], Option[String]) => Future[EmptyResponse])
                      (implicit ec: ExecutionContext, user: LoggedUser): Future[EmptyResponse] = {
    processRepository.fetchLatestProcessDetailsForProcessId[Unit](processIdWithName.id).flatMap {
      case Some(process) if process.isArchived =>
        Future(Left(ProcessIllegalAction.archived(action, processIdWithName)))
      case Some(process) if process.isSubprocess =>
        Future(Left(ProcessIllegalAction.subprocess(action, processIdWithName)))
      case Some(_) =>
        getProcessState(processIdWithName).flatMap(ps => {
          if (ps.allowedActions.contains(action)) {
            actionToDo(processIdWithName, savepointPath, comment)
          } else {
            Future(Left(ProcessIllegalAction(action, processIdWithName, ps)))
          }
        })
      case None =>
        Future(Left(ProcessNotFoundError(processIdWithName.id.value.toString)))
    }
  }
}
