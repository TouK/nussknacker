package pl.touk.nussknacker.ui.process

import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.{ProcessActionType, ProcessState}
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails
import pl.touk.nussknacker.ui.EspError.XError
import pl.touk.nussknacker.ui.process.deployment.{Cancel, CheckStatus, Deploy}
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.process.repository.{FetchingProcessRepository, ProcessActionRepository, TransactionSupport, WriteProcessRepository}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import scala.concurrent.{ExecutionContext, Future}
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.ui.process.exception.ProcessIllegalAction
import akka.actor.ActorRef
import akka.pattern.ask

import java.time
import scala.language.higherKinds

/**
  * ProcessService provides functionality for archive, unarchive, deploy, cancel process.
  * Each action includes verification based on actual process state and checking process is subprocess / archived.
  *
  * FIXME: Refactor F[_] - it should be split
  */
class ProcessService[F[_]](managerActor: ActorRef,
                           requestTimeLimit: time.Duration,
                           transactionSupport: TransactionSupport[F],
                           processRepository: FetchingProcessRepository[Future],
                           processActionRepository: ProcessActionRepository[F],
                           writeRepository: WriteProcessRepository[F]) extends LazyLogging {

  type EmptyResponse = XError[Unit]

  import pl.touk.nussknacker.engine.api.CirceUtil._
  import scala.concurrent.duration._
  import cats.syntax.either._

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
        doOnProcessStateVerification(process, ProcessActionType.Archive)(process => {
          writeRepository.archive(processId = process.idWithName.id, isArchived = true)
        })
      case None =>
        Future(Left(ProcessNotFoundError(processIdWithName.id.value.toString)))
    }
  }

  private def doArchive(process: BaseProcessDetails[_])(implicit ec: ExecutionContext, user: LoggedUser): Future[EmptyResponse] = {
    transactionSupport.runInTransaction(
      writeRepository.archive(processId = process.idWithName.id, isArchived = true),
      processActionRepository.markProcessAsArchived(processId = process.idWithName.id, process.processVersionId)
    ).map(_ => ().asRight)
  }

  private def archiveSubprocess(process: BaseProcessDetails[_])(implicit ec: ExecutionContext, user: LoggedUser): Future[EmptyResponse] =
    writeRepository.archive(processId = process.idWithName.id, isArchived = true)

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


  def unArchiveProcess(processIdWithName: ProcessIdWithName)(implicit ec: ExecutionContext, user: LoggedUser): Future[EmptyResponse] = {
    processRepository.fetchLatestProcessDetailsForProcessId[Unit](processIdWithName.id).flatMap {
      case Some(process) if !process.isArchived =>
        Future(Left(ProcessIllegalAction("Can't unarchive not archived process.")))
      case Some(process) =>
        transactionSupport.runInTransaction(
          writeRepository.archive(processId = process.idWithName.id, isArchived = false),
          processActionRepository.markProcessAsUnArchived(processId = process.idWithName.id, process.processVersionId)
        ).map(_ => ().asRight)
      case None =>
        Future(Left(ProcessNotFoundError(processIdWithName.id.value.toString)))
    }
  }

  def deployProcess(processIdWithName: ProcessIdWithName, savepointPath: Option[String], comment: Option[String])(implicit ec: ExecutionContext, user: LoggedUser): Future[EmptyResponse] =
     doAction(ProcessActionType.Deploy, processIdWithName, savepointPath, comment){ (processIdWithName: ProcessIdWithName, savepointPath: Option[String], comment: Option[String]) =>
       (managerActor ? Deploy(processIdWithName, user, savepointPath, comment))
         .map(_ => ().asRight)
     }

  def cancelProcess(processIdWithName: ProcessIdWithName, comment: Option[String])(implicit ec: ExecutionContext, user: LoggedUser): Future[EmptyResponse] =
   doAction(ProcessActionType.Cancel, processIdWithName, None, comment){ (processIdWithName: ProcessIdWithName, _: Option[String], comment: Option[String]) =>
     (managerActor ? Cancel(processIdWithName, user, comment))
       .map(_ => ().asRight)
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
