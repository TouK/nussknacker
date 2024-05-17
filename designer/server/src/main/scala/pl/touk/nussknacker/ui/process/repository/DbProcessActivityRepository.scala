package pl.touk.nussknacker.ui.process.repository

import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.ui.process.ScenarioAttachmentService.AttachmentToAdd
import pl.touk.nussknacker.ui.db.entity.{AttachmentEntityData, CommentActions, CommentEntityData}
import pl.touk.nussknacker.ui.db.{DbRef, NuTables}
import pl.touk.nussknacker.ui.listener.{Comment => CommentValue}
import pl.touk.nussknacker.ui.process.repository.DbProcessActivityRepository.{Attachment, Comment, ProcessActivity}
import pl.touk.nussknacker.ui.security.api.{ImpersonatedUser, LoggedUser, RealLoggedUser}

import java.sql.Timestamp
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

trait ProcessActivityRepository {

  def addComment(processId: ProcessId, processVersionId: VersionId, comment: CommentValue)(
      implicit ec: ExecutionContext,
      loggedUser: LoggedUser
  ): Future[Unit]

  def deleteComment(commentId: Long)(implicit ec: ExecutionContext): Future[Either[Exception, Unit]]
  def findActivity(processId: ProcessId)(implicit ec: ExecutionContext): Future[ProcessActivity]

  def addAttachment(
      attachmentToAdd: AttachmentToAdd
  )(implicit ec: ExecutionContext, loggedUser: LoggedUser): Future[Unit]

  def findAttachment(attachmentId: Long, scenarioId: ProcessId)(
      implicit ec: ExecutionContext
  ): Future[Option[AttachmentEntityData]]

}

final case class DbProcessActivityRepository(protected val dbRef: DbRef)
    extends ProcessActivityRepository
    with LazyLogging
    with BasicRepository
    with NuTables
    with CommentActions {

  import profile.api._

  override def addComment(processId: ProcessId, processVersionId: VersionId, comment: CommentValue)(
      implicit ec: ExecutionContext,
      loggedUser: LoggedUser
  ): Future[Unit] = {
    run(newCommentAction(processId, processVersionId, Option(comment))).map(_ => ())
  }

  override def deleteComment(commentId: Long)(implicit ec: ExecutionContext): Future[Either[Exception, Unit]] = {
    val commentToDelete = commentsTable.filter(_.id === commentId)
    val deleteAction    = commentToDelete.delete
    run(deleteAction).map { deletedRowsCount =>
      logger.info(s"Tried to delete comment with id: $commentId. Deleted rows count: $deletedRowsCount")
      if (deletedRowsCount == 0) {
        Left(new RuntimeException(s"Unable to delete comment with id: $commentId"))
      } else {
        Right(())
      }
    }
  }

  override def findActivity(processId: ProcessId)(implicit ec: ExecutionContext): Future[ProcessActivity] = {
    val findProcessActivityAction = for {
      fetchedComments    <- commentsTable.filter(_.processId === processId).sortBy(_.createDate.desc).result
      fetchedAttachments <- attachmentsTable.filter(_.processId === processId).sortBy(_.createDate.desc).result
      comments    = fetchedComments.map(c => Comment(c)).toList
      attachments = fetchedAttachments.map(c => Attachment(c)).toList
    } yield ProcessActivity(comments, attachments)

    run(findProcessActivityAction)
  }

  override def addAttachment(
      attachmentToAdd: AttachmentToAdd
  )(implicit ec: ExecutionContext, loggedUser: LoggedUser): Future[Unit] = {
    val addAttachmentAction = for {
      _ <- attachmentsTable += AttachmentEntityData(
        id = -1L,
        processId = attachmentToAdd.scenarioId,
        processVersionId = attachmentToAdd.scenarioVersionId,
        fileName = attachmentToAdd.fileName,
        data = attachmentToAdd.data,
        user = loggedUser.username,
        impersonatedBy = loggedUser match {
          case _: RealLoggedUser   => None
          case u: ImpersonatedUser => Some(u.impersonatingUser.id)
        },
        createDate = Timestamp.from(Instant.now())
      )
    } yield ()

    run(addAttachmentAction)
  }

  override def findAttachment(
      attachmentId: Long,
      scenarioId: ProcessId
  )(implicit ec: ExecutionContext): Future[Option[AttachmentEntityData]] = {
    val findAttachmentAction = attachmentsTable
      .filter(_.id === attachmentId)
      .filter(_.processId === scenarioId)
      .result
      .headOption
    run(findAttachmentAction)
  }

}

object DbProcessActivityRepository {

  @JsonCodec final case class ProcessActivity(comments: List[Comment], attachments: List[Attachment])

  @JsonCodec final case class Attachment(
      id: Long,
      processVersionId: VersionId,
      fileName: String,
      user: String,
      createDate: Instant
  )

  object Attachment {

    def apply(attachment: AttachmentEntityData): Attachment = {
      Attachment(
        id = attachment.id,
        processVersionId = attachment.processVersionId,
        fileName = attachment.fileName,
        user = attachment.user,
        createDate = attachment.createDateTime
      )
    }

  }

  @JsonCodec final case class Comment(
      id: Long,
      processVersionId: VersionId,
      content: String,
      user: String,
      createDate: Instant
  )

  object Comment {

    def apply(comment: CommentEntityData): Comment = {
      Comment(
        id = comment.id,
        processVersionId = comment.processVersionId,
        content = comment.content,
        user = comment.user,
        createDate = comment.createDateTime
      )
    }

  }

}
