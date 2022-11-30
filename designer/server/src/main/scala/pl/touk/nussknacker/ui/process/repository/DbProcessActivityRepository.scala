package pl.touk.nussknacker.ui.process.repository

import java.sql.Timestamp
import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.ui.api.ProcessAttachmentService.AttachmentToAdd
import pl.touk.nussknacker.ui.db.entity.{AttachmentEntityData, CommentActions, CommentEntityData}
import pl.touk.nussknacker.ui.db.{DbConfig, EspTables}
import pl.touk.nussknacker.ui.process.repository.DbProcessActivityRepository.{Attachment, Comment, ProcessActivity}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.listener.{Comment => CommentValue}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

trait ProcessActivityRepository {
  def addComment(processId: ProcessId, processVersionId: VersionId, comment: CommentValue)(implicit ec: ExecutionContext, loggedUser: LoggedUser): Future[Unit]
  def deleteComment(commentId: Long)(implicit ec: ExecutionContext): Future[Unit]
  def findActivity(processId: ProcessIdWithName)(implicit ec: ExecutionContext): Future[ProcessActivity]
  def addAttachment(attachmentToAdd: AttachmentToAdd)(implicit ec: ExecutionContext, loggedUser: LoggedUser): Future[Unit]
  def findAttachment(attachmentId: Long)(implicit ec: ExecutionContext): Future[Option[AttachmentEntityData]]
}

case class DbProcessActivityRepository(dbConfig: DbConfig)
  extends ProcessActivityRepository with LazyLogging with BasicRepository with EspTables with CommentActions {

  import profile.api._

  override def addComment(processId: ProcessId, processVersionId: VersionId, comment: CommentValue)
                (implicit ec: ExecutionContext, loggedUser: LoggedUser): Future[Unit] = {
    run(newCommentAction(processId, processVersionId, Option(comment))).map(_ => ())
  }

  override def deleteComment(commentId: Long)(implicit ec: ExecutionContext): Future[Unit] = {
    val commentToDelete = commentsTable.filter(_.id === commentId)
    val deleteAction = commentToDelete.delete
    run(deleteAction).flatMap { deletedRowsCount =>
      logger.info(s"Tried to delete comment with id: $commentId. Deleted rows count: $deletedRowsCount")
      if (deletedRowsCount == 0) {
        Future.failed(new RuntimeException(s"Unable to delete comment with id: $commentId"))
      } else {
        Future.successful(())
      }
    }
  }

  override def findActivity(processId: ProcessIdWithName)(implicit ec: ExecutionContext): Future[ProcessActivity] = {
    val findProcessActivityAction = for {
      fetchedComments <- commentsTable.filter(_.processId === processId.id).sortBy(_.createDate.desc).result
      fetchedAttachments <- attachmentsTable.filter(_.processId === processId.id).sortBy(_.createDate.desc).result
      comments = fetchedComments.map(c => Comment(c, processId.name.value)).toList
      attachments = fetchedAttachments.map(c => Attachment(c, processId.name.value)).toList
    } yield ProcessActivity(comments, attachments)

    run(findProcessActivityAction)
  }

  override def addAttachment(attachmentToAdd: AttachmentToAdd)(implicit ec: ExecutionContext, loggedUser: LoggedUser): Future[Unit] = {
    val addAttachmentAction = for {
      _ <- attachmentsTable += AttachmentEntityData(
        id = -1L,
        processId = attachmentToAdd.processId,
        processVersionId = attachmentToAdd.processVersionId,
        fileName = attachmentToAdd.fileName,
        data = attachmentToAdd.data,
        user = loggedUser.username,
        createDate = Timestamp.from(Instant.now())
      )
    } yield ()

    run(addAttachmentAction)
  }

  override def findAttachment(attachmentId: Long)(implicit ec: ExecutionContext): Future[Option[AttachmentEntityData]] = {
    val findAttachmentAction = attachmentsTable.filter(_.id === attachmentId).result.headOption
    run(findAttachmentAction)
  }
}

object DbProcessActivityRepository {

  @JsonCodec case class ProcessActivity(comments: List[Comment], attachments: List[Attachment])

  @JsonCodec  case class Attachment(id: Long, processId: String, processVersionId: VersionId, fileName: String, user: String, createDate: Instant)

  object Attachment {
    def apply(attachment: AttachmentEntityData, processName: String): Attachment = {
      Attachment(
        id = attachment.id,
        processId = processName,
        processVersionId = attachment.processVersionId,
        fileName = attachment.fileName,
        user = attachment.user,
        createDate = attachment.createDateTime
      )
    }
  }

  @JsonCodec case class Comment(id: Long, processId: String, processVersionId: VersionId, content: String, user: String, createDate: Instant)
  object Comment {
    def apply(comment: CommentEntityData, processName: String): Comment = {
      Comment(
        id = comment.id,
        processId = processName,
        processVersionId = comment.processVersionId,
        content = comment.content,
        user = comment.user,
        createDate = comment.createDateTime
      )
    }
  }
}
