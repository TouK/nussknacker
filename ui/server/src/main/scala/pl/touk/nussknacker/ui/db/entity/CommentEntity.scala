package pl.touk.nussknacker.ui.db.entity

import java.sql.Timestamp
import java.time.LocalDateTime

import db.migration.DefaultJdbcProfile.profile.api._
import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.ui.db.EspTables.commentsTable
import pl.touk.nussknacker.ui.process.ProcessId
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.DateUtils
import slick.sql.SqlProfile.ColumnOption.NotNull

import scala.concurrent.ExecutionContext

object CommentEntity {

  val nextIdAction: DBIO[Long] = {
    sql"""select "process_comments_id_sequence".nextval from dual""".as[Long].head
  }

  class CommentEntity(tag: Tag) extends Table[CommentEntityData](tag, "process_comments") {

    def id = column[Long]("id", O.PrimaryKey)

    def processId = column[Long]("process_id", NotNull)

    def processVersionId = column[Long]("process_version_id", NotNull)

    def content = column[String]("content", NotNull)

    def createDate = column[Timestamp]("create_date", NotNull)

    def user = column[String]("user", NotNull)

    def * = (id, processId, processVersionId, content, user, createDate) <> (CommentEntityData.tupled, CommentEntityData.unapply)

  }

  case class CommentEntityData(id: Long, processId: Long, processVersionId: Long, content: String, user: String, createDate: Timestamp) {
    val createDateTime = DateUtils.toLocalDateTime(createDate)
  }

  def newCommentAction(processId: ProcessId, processVersionId: Long, comment: String)
                  (implicit ec: ExecutionContext, loggedUser: LoggedUser): DB[Unit] = {
    if (comment.nonEmpty) {
      val addCommentAction = for {
        newId <- CommentEntity.nextIdAction
        _ <- commentsTable += CommentEntityData(
          id = newId,
          processId = processId.value,
          processVersionId = processVersionId,
          content = comment,
          user = loggedUser.id,
          createDate = Timestamp.valueOf(LocalDateTime.now())
        )
      } yield ()
      addCommentAction.map(_ => ())
    } else {
      DBIO.successful(())
    }
  }

}