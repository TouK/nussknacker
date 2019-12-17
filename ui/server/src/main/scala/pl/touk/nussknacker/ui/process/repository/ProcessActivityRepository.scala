package pl.touk.nussknacker.ui.process.repository

import java.sql.Timestamp
import java.time.LocalDateTime

import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.JsonCodec
import io.circe.java8.time.{JavaTimeDecoders, JavaTimeEncoders}
import pl.touk.nussknacker.restmodel.process.{ProcessId, ProcessIdWithName}
import pl.touk.nussknacker.ui.api.ProcessAttachmentService.AttachmentToAdd
import pl.touk.nussknacker.ui.db.entity.{AttachmentEntityData, CommentActions, CommentEntityData}
import pl.touk.nussknacker.ui.db.{DbConfig, EspTables}
import pl.touk.nussknacker.ui.process.repository.ProcessActivityRepository.{Attachment, Comment, ProcessActivity}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

case class ProcessActivityRepository(dbConfig: DbConfig) 
  extends LazyLogging with BasicRepository with EspTables with CommentActions {

  import profile.api._
  
  def addComment(processId: ProcessId, processVersionId: Long, comment: String)
                (implicit ec: ExecutionContext, loggedUser: LoggedUser): Future[Unit] = {
    run(newCommentAction(processId, processVersionId, comment)).map(_ => ())
  }

  def deleteComment(commentId: Long)(implicit ec: ExecutionContext): Future[Unit] = {
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

  def findActivity(processId: ProcessIdWithName)(implicit ec: ExecutionContext): Future[ProcessActivity] = {
    val findProcessActivityAction = for {
      fetchedComments <- commentsTable.filter(_.processId === processId.id.value).sortBy(_.createDate.desc).result
      fetchedAttachments <- attachmentsTable.filter(_.processId === processId.id.value).sortBy(_.createDate.desc).result
      comments = fetchedComments.map(c => Comment(c, processId.name.value)).toList
      attachments = fetchedAttachments.map(c => Attachment(c, processId.name.value)).toList
    } yield ProcessActivity(comments, attachments)

    run(findProcessActivityAction)
  }

  def addAttachment(attachmentToAdd: AttachmentToAdd)(implicit ec: ExecutionContext, loggedUser: LoggedUser): Future[Unit] = {
    val addAttachmentAction = for {
      attachmentCount <- attachmentsTable.length.result
      _ <- attachmentsTable += AttachmentEntityData(
        id = attachmentCount.toLong,
        processId = attachmentToAdd.processId,
        processVersionId = attachmentToAdd.processVersionId,
        fileName = attachmentToAdd.fileName,
        filePath = attachmentToAdd.relativeFilePath,
        user = loggedUser.username,
        createDate = Timestamp.valueOf(LocalDateTime.now())
      )
    } yield ()

    run(addAttachmentAction)
  }

  def findAttachment(attachmentId: Long)(implicit ec: ExecutionContext): Future[Option[AttachmentEntityData]] = {
    val findAttachmentAction = attachmentsTable.filter(_.id === attachmentId).result.headOption
    run(findAttachmentAction)
  }
}

object ProcessActivityRepository extends JavaTimeDecoders with JavaTimeEncoders {

  @JsonCodec case class ProcessActivity(comments: List[Comment], attachments: List[Attachment])

  @JsonCodec  case class Attachment(id: Long, processId: String, processVersionId: Long, fileName: String, user: String, createDate: LocalDateTime)

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

  @JsonCodec case class Comment(id: Long, processId: String, processVersionId: Long, content: String, user: String, createDate: LocalDateTime)
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