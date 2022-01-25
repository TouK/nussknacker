package pl.touk.nussknacker.ui.db.entity

import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.ui.db.DateUtils
import pl.touk.nussknacker.ui.security.api.LoggedUser
import slick.jdbc.JdbcProfile
import slick.lifted.{TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.NotNull

import java.sql.Timestamp
import java.time.LocalDateTime
import scala.concurrent.ExecutionContext

trait CommentEntityFactory extends BaseEntityFactory {

  import profile.api._

  val commentsTable: LTableQuery[CommentEntityFactory#CommentEntity] = LTableQuery(new CommentEntity(_))

  class CommentEntity(tag: Tag) extends Table[CommentEntityData](tag, "process_comments") {

    def id: Rep[Long] = column[Long]("id", O.PrimaryKey)

    def processId: Rep[ProcessId] = column[ProcessId]("process_id", NotNull)

    def processVersionId: Rep[VersionId] = column[VersionId]("process_version_id", NotNull)

    def content: Rep[String] = column[String]("content", NotNull)

    def createDate: Rep[Timestamp] = column[Timestamp]("create_date", NotNull)

    def user: Rep[String] = column[String]("user", NotNull)

    override def * = (id, processId, processVersionId, content, user, createDate) <> (
      CommentEntityData.apply _ tupled, CommentEntityData.unapply
    )

  }

}


case class CommentEntityData(id: Long, processId: ProcessId, processVersionId: VersionId, content: String, user: String, createDate: Timestamp) {
  val createDateTime: LocalDateTime = DateUtils.toLocalDateTime(createDate)
}

trait CommentActions {
  protected val profile: JdbcProfile
  import profile.api._
  
  val commentsTable: LTableQuery[CommentEntityFactory#CommentEntity]
  
  def nextIdAction[T <: JdbcProfile](implicit jdbcProfile: T): DBIO[Long] = {
    Sequence[Long]("process_comments_id_sequence").next.result
  }

  def newCommentAction(processId: ProcessId, processVersionId: VersionId, comment: String)
                      (implicit ec: ExecutionContext, loggedUser: LoggedUser): DB[Option[Long]] = {
    if (comment.nonEmpty) {
      for {
        newId <- nextIdAction
        _ <- commentsTable += CommentEntityData(
          id = newId,
          processId = processId,
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
