package pl.touk.nussknacker.ui.db.entity

import java.sql.Timestamp
import java.time.LocalDateTime

import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.restmodel.process.ProcessId
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.DateUtils
import slick.jdbc.{HsqldbProfile, JdbcProfile, PostgresProfile}
import slick.lifted.{TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.NotNull

import scala.concurrent.ExecutionContext

trait CommentEntityFactory {
  protected val profile: JdbcProfile

  import profile.api._

  class CommentEntity(tag: Tag) extends Table[CommentEntityData](tag, "process_comments") {

    def id: Rep[Long] = column[Long]("id", O.PrimaryKey)

    def processId: Rep[Long] = column[Long]("process_id", NotNull)

    def processVersionId: Rep[Long] = column[Long]("process_version_id", NotNull)

    def content: Rep[String] = column[String]("content", NotNull)

    def createDate: Rep[Timestamp] = column[Timestamp]("create_date", NotNull)

    def user: Rep[String] = column[String]("user", NotNull)

    override def * = (id, processId, processVersionId, content, user, createDate) <> (CommentEntityData.tupled, CommentEntityData.unapply)

  }

  val commentsTable: LTableQuery[CommentEntityFactory#CommentEntity] = LTableQuery(new CommentEntity(_))
}

case class CommentEntityData(id: Long, processId: Long, processVersionId: Long, content: String, user: String, createDate: Timestamp) {
  val createDateTime: LocalDateTime = DateUtils.toLocalDateTime(createDate)
}

trait CommentActions {
  protected val profile: JdbcProfile
  import profile.api._
  
  val commentsTable: LTableQuery[CommentEntityFactory#CommentEntity]
  
  def nextIdAction[T <: JdbcProfile](implicit jdbcProfile: T): DBIO[Long] = {
    Sequence[Long]("process_comments_id_sequence").next.result
  }

  def newCommentAction(processId: ProcessId, processVersionId: Long, comment: String)
                      (implicit ec: ExecutionContext,
                       loggedUser: LoggedUser): DB[Option[Long]] = {
    if (comment.nonEmpty) {
      for {
        newId <- nextIdAction
        _ <- commentsTable += CommentEntityData(
          id = newId,
          processId = processId.value,
          processVersionId = processVersionId,
          content = comment,
          user = loggedUser.username,
          createDate = Timestamp.valueOf(LocalDateTime.now())
        )
      } yield Some(newId)
    } else {
      DBIO.successful(None)
    }
  }

}