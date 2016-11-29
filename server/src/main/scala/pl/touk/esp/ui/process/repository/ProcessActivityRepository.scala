package pl.touk.esp.ui.process.repository

import java.sql.Timestamp
import java.time.LocalDateTime

import pl.touk.esp.ui.db.EspTables._
import pl.touk.esp.ui.db.entity.CommentEntity.CommentEntityData
import pl.touk.esp.ui.process.repository.ProcessActivityRepository.{Comment, ProcessActivity}
import pl.touk.esp.ui.security.LoggedUser
import slick.jdbc.{JdbcBackend, JdbcProfile}

import scala.concurrent.{ExecutionContext, Future}

class ProcessActivityRepository(db: JdbcBackend.Database,
                                driver: JdbcProfile) {

  import driver.api._

  def addComment(processId: String, processVersionId: Long, comment: String)
                (implicit ec: ExecutionContext, loggedUser: LoggedUser): Future[Unit] = {
    val addCommentAction = commentsTable += CommentEntityData(
      processId = processId,
      processVersionId = processVersionId,
      content = comment,
      user = loggedUser.id,
      createDate = Timestamp.valueOf(LocalDateTime.now())
    )
    db.run(addCommentAction).map(_ => ())
  }

  def findActivity(processId: String)(implicit ec: ExecutionContext): Future[ProcessActivity] = {
    val findCommentAction = commentsTable.filter(_.processId === processId).sortBy(_.createDate.desc).result
    db.run(findCommentAction).map(commentsEntity => {
      val comments = commentsEntity.map(c => Comment(c)).toList
      ProcessActivity(comments)
    })
  }
}

object ProcessActivityRepository {
  case class ProcessActivity(comments: List[Comment])

  case class Comment(processId: String, processVersionId: Long, content: String, user: String, createDate: LocalDateTime)
  object Comment {
    def apply(comment: CommentEntityData): Comment = {
      Comment(
        processId = comment.processId,
        processVersionId = comment.processVersionId,
        content = comment.content,
        user = comment.user,
        createDate = comment.createDateTime
      )
    }
  }
}