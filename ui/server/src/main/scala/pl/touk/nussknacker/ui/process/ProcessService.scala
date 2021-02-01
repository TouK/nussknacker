package pl.touk.nussknacker.ui.process

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails
import pl.touk.nussknacker.ui.EspError.XError
import pl.touk.nussknacker.ui.process.deployment.ManagementService
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.process.repository.{FetchingProcessRepository, ProcessActionRepository, WriteProcessRepository}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.ui.process.exception.ProcessIllegalAction

class ProcessService(managementService: ManagementService,
                     processRepository: FetchingProcessRepository[Future],
                     processActionRepository: ProcessActionRepository,
                     writeRepository: WriteProcessRepository) extends LazyLogging {

  type EmptyResponse = XError[Unit]
  type DeployResponse = XError[Any]

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
    managementService.getProcessState(process.idWithName).flatMap(state => {
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
       managementService
         .deployProcess(processIdWithName, savepointPath, comment)
         .map(_ => Right(Unit))
     }

  def cancelProcess(processIdWithName: ProcessIdWithName, comment: Option[String])(implicit ec: ExecutionContext, user: LoggedUser): Future[EmptyResponse] =
   doAction(ProcessActionType.Cancel, processIdWithName, None, comment){ (processIdWithName: ProcessIdWithName, _: Option[String], comment: Option[String]) =>
     managementService
       .cancelProcess(processIdWithName, comment)
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
        managementService.getProcessState(processIdWithName).flatMap(ps => {
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
