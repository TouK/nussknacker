package pl.touk.nussknacker.ui.process.repository

import java.sql.Timestamp
import java.time.LocalDateTime

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.api.ProcessAttachmentService.AttachmentToAdd
import pl.touk.nussknacker.ui.db.EspTables._
import pl.touk.nussknacker.ui.db.entity.AttachmentEntity.AttachmentEntityData
import pl.touk.nussknacker.ui.db.entity.CommentEntity
import pl.touk.nussknacker.ui.db.entity.CommentEntity.CommentEntityData
import pl.touk.nussknacker.ui.process.repository.ProcessActivityRepository.{Attachment, Comment, ProcessActivity}
import pl.touk.nussknacker.ui.security.LoggedUser
import slick.jdbc.{JdbcBackend, JdbcProfile}

import scala.concurrent.{ExecutionContext, Future}

class ProcessActivityRepository(db: JdbcBackend.Database,
                                driver: JdbcProfile) extends LazyLogging {

  import driver.api._

  def addComment(processId: String, processVersionId: Long, comment: String)
                (implicit ec: ExecutionContext, loggedUser: LoggedUser): Future[Unit] = {
    if (comment.nonEmpty) {
      val addCommentAction = for {
        newId <- CommentEntity.nextIdAction
        _ <- commentsTable += CommentEntityData(
          id = newId,
          processId = processId,
          processVersionId = processVersionId,
          content = comment,
          user = loggedUser.id,
          createDate = Timestamp.valueOf(LocalDateTime.now())
        )
      } yield ()
      db.run(addCommentAction).map(_ => ())
    } else {
      Future.successful(())
    }
  }

  def deleteComment(commentId: Long)(implicit ec: ExecutionContext): Future[Unit] = {
    val commentToDelete = commentsTable.filter(_.id === commentId)
    val deleteAction = commentToDelete.delete
    db.run(deleteAction).flatMap { deletedRowsCount =>
      logger.info(s"Tried to delete comment with id: ${commentId}. Deleted rows count: $deletedRowsCount")
      if (deletedRowsCount == 0) {
        Future.failed(new RuntimeException(s"Unable to delete comment with id: $commentId"))
      } else {
        Future.successful(())
      }
    }
  }

  def findActivity(processId: String)(implicit ec: ExecutionContext): Future[ProcessActivity] = {
    val findProcessActivityAction = for {
      fetchedComments <- commentsTable.filter(_.processId === processId).sortBy(_.createDate.desc).result
      fetchedAttachments <- attachmentsTable.filter(_.processId === processId).sortBy(_.createDate.desc).result
      comments = fetchedComments.map(c => Comment(c)).toList
      attachments = fetchedAttachments.map(c => Attachment(c)).toList
    } yield ProcessActivity(comments, attachments)

    db.run(findProcessActivityAction)
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
        user = loggedUser.id,
        createDate = Timestamp.valueOf(LocalDateTime.now())
      )
    } yield ()

    db.run(addAttachmentAction)
  }

  def findAttachment(attachmentId: Long)(implicit ec: ExecutionContext): Future[Option[AttachmentEntityData]] = {
    val findAttachmentAction = attachmentsTable.filter(_.id === attachmentId).result.headOption
    db.run(findAttachmentAction)
  }
}

object ProcessActivityRepository {
  case class ProcessActivity(comments: List[Comment], attachments: List[Attachment])

  case class Attachment(id: Long, processId: String, processVersionId: Long, fileName: String, user: String, createDate: LocalDateTime)
  object Attachment {
    def apply(attachment: AttachmentEntityData): Attachment = {
      Attachment(
        id = attachment.id,
        processId = attachment.processId,
        processVersionId = attachment.processVersionId,
        fileName = attachment.fileName,
        user = attachment.user,
        createDate = attachment.createDateTime
      )
    }
  }

  case class Comment(id: Long, processId: String, processVersionId: Long, content: String, user: String, createDate: LocalDateTime)
  object Comment {
    def apply(comment: CommentEntityData): Comment = {
      Comment(
        id = comment.id,
        processId = comment.processId,
        processVersionId = comment.processVersionId,
        content = comment.content,
        user = comment.user,
        createDate = comment.createDateTime
      )
    }
  }
}