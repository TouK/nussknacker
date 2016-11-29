package pl.touk.esp.ui.db.entity

import java.sql.Timestamp

import db.migration.DefaultJdbcProfile.profile.api._
import pl.touk.esp.ui.util.DateUtils
import slick.sql.SqlProfile.ColumnOption.NotNull

object CommentEntity {

  class CommentEntity(tag: Tag) extends Table[CommentEntityData](tag, "process_comments") {

    def processId = column[String]("process_id", NotNull)

    def processVersionId = column[Long]("process_version_id", NotNull)

    def content = column[String]("content", NotNull)

    def createDate = column[Timestamp]("create_date", NotNull)

    def user = column[String]("user", NotNull)

    def * = (processId, processVersionId, content, user, createDate) <> (CommentEntityData.tupled, CommentEntityData.unapply)

  }

  case class CommentEntityData(processId: String, processVersionId: Long, content: String, user: String, createDate: Timestamp) {
    val createDateTime = DateUtils.toLocalDateTime(createDate)
  }

}