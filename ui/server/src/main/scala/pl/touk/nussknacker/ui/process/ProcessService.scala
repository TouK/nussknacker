package pl.touk.nussknacker.ui.process

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.{ProcessActionType, ProcessState}
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails
import pl.touk.nussknacker.ui.EspError.XError
import pl.touk.nussknacker.ui.IllegalOperationError
import pl.touk.nussknacker.ui.process.deployment.CheckStatus
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.process.repository.{FetchingProcessRepository, ProcessActionRepository, WriteProcessRepository}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType

class ProcessService(managerActor: ActorRef,
                     processRepository: FetchingProcessRepository[Future],
                     processActionRepository: ProcessActionRepository,
                     writeRepository: WriteProcessRepository) extends LazyLogging {
  import scala.concurrent.duration._

  type ArchiveResponse = XError[Unit]

  /**
    * Handling error at retrieving status from manager is created at ManagementActor
    */
  def getProcessState(processIdWithName: ProcessIdWithName)(implicit ec: ExecutionContext, user: LoggedUser): Future[ProcessState] = {
    implicit val timeout: Timeout = Timeout(1 minute)
    (managerActor ? CheckStatus(processIdWithName, user)).mapTo[ProcessState]
  }

  def archiveProcess(processIdWithName: ProcessIdWithName)(implicit ec: ExecutionContext, user: LoggedUser): Future[ArchiveResponse] = {
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
  private def archiveSubprocess(process: BaseProcessDetails[_])(implicit ec: ExecutionContext, user: LoggedUser): Future[ArchiveResponse] =
    doArchive(process)

  private def doOnProcessStateVerification(process: BaseProcessDetails[_], actionToCheck: ProcessActionType)
                                          (action: BaseProcessDetails[_] => Future[ArchiveResponse])
                                          (implicit ec: ExecutionContext, user: LoggedUser): Future[ArchiveResponse] =
    getProcessState(process.idWithName).flatMap(state => {
      if (state.allowedActions.contains(actionToCheck)) {
        action(process)
      } else {
        Future(Left(ProcessIllegalAction(process.idWithName, state)))
      }
    })

  //FIXME: Right now we don't do it in transaction..
  private def doArchive(process: BaseProcessDetails[_])(implicit ec: ExecutionContext, user: LoggedUser): Future[ArchiveResponse] =
    for {
      archive <- writeRepository.archive(processId = process.idWithName.id, isArchived = true)
      _ <- processActionRepository.markProcessAsArchived(process.idWithName.id, process.processVersionId, None)
    } yield archive

  def unArchiveProcess(processIdWithName: ProcessIdWithName)(implicit ec: ExecutionContext, user: LoggedUser): Future[ArchiveResponse] = {
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
  private def doUnArchive(process: BaseProcessDetails[_])(implicit ec: ExecutionContext, user: LoggedUser): Future[ArchiveResponse] =
    for {
      archive <- writeRepository.archive(processId = process.idWithName.id, isArchived = false)
      _ <- processActionRepository.markProcessAsUnArchived(process.idWithName.id, process.processVersionId, None)
    } yield archive
}

object ProcessIllegalAction {
  def apply(processIdWithName: ProcessIdWithName, state: ProcessState): ProcessIllegalAction =
    ProcessIllegalAction(s"Can't archive process ${processIdWithName.name.value} with state: ${state.status.name}, allowed actions for process: ${state.allowedActions.mkString(",")}.")
}

case class ProcessIllegalAction(message: String) extends Exception(message) with IllegalOperationError
