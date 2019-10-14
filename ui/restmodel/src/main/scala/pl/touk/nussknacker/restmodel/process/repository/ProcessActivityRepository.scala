package pl.touk.nussknacker.restmodel.process.repository

import pl.touk.nussknacker.restmodel.api.AttachmentToAdd
import pl.touk.nussknacker.restmodel.db.entity.AttachmentEntityData
import pl.touk.nussknacker.restmodel.process.{ProcessActivity, ProcessId, ProcessIdWithName}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

trait ProcessActivityRepository {
  def addComment(processId: ProcessId, processVersionId: Long, comment: String)
                (implicit ec: ExecutionContext, loggedUser: LoggedUser): Future[Unit]

  def deleteComment(commentId: Long)(implicit ec: ExecutionContext): Future[Unit]

  def findActivity(processId: ProcessIdWithName)(implicit ec: ExecutionContext): Future[ProcessActivity]

  def addAttachment(attachmentToAdd: AttachmentToAdd)(implicit ec: ExecutionContext, loggedUser: LoggedUser): Future[Unit]

  def findAttachment(attachmentId: Long)(implicit ec: ExecutionContext): Future[Option[AttachmentEntityData]]
}
