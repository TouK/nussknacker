package pl.touk.esp.ui.db.entity

import java.sql.Timestamp

import db.migration.DefaultJdbcProfile.profile.api._
import pl.touk.esp.ui.util.DateUtils
import slick.sql.SqlProfile.ColumnOption.NotNull

object CommentEntity {

  val nextIdAction: DBIO[Long] = {
    sql"""select "process_comments_id_sequence".nextval from dual""".as[Long].head
  }

  class CommentEntity(tag: Tag) extends Table[CommentEntityData](tag, "process_comments") {

    def id = column[Long]("id", O.PrimaryKey)

    def processId = column[String]("process_id", NotNull)

    def processVersionId = column[Long]("process_version_id", NotNull)

    def content = column[String]("content", NotNull)

    def createDate = column[Timestamp]("create_date", NotNull)

    def user = column[String]("user", NotNull)

    def * = (id, processId, processVersionId, content, user, createDate) <> (CommentEntityData.tupled, CommentEntityData.unapply)

  }

  case class CommentEntityData(id: Long, processId: String, processVersionId: Long, content: String, user: String, createDate: Timestamp) {
    val createDateTime = DateUtils.toLocalDateTime(createDate)
  }

}