package pl.touk.nussknacker.ui.db.entity

import java.sql.Timestamp

import db.migration.DefaultJdbcProfile.profile.api._
import pl.touk.nussknacker.ui.util.DateUtils
import slick.ast.ColumnOption.PrimaryKey
import slick.sql.SqlProfile.ColumnOption.NotNull

object AttachmentEntity {

  class AttachmentEntity(tag: Tag) extends Table[AttachmentEntityData](tag, "process_attachments") {

    def id = column[Long]("id", PrimaryKey)

    def processId = column[String]("process_id", NotNull)

    def processVersionId = column[Long]("process_version_id", NotNull)

    def fileName = column[String]("file_name", NotNull)

    def filePath = column[String]("file_path", NotNull)

    def createDate = column[Timestamp]("create_date", NotNull)

    def user = column[String]("user", NotNull)

    def * = (id, processId, processVersionId, fileName, filePath, user, createDate) <> (AttachmentEntityData.tupled, AttachmentEntityData.unapply)

  }

  case class AttachmentEntityData(id: Long, processId: String, processVersionId: Long, fileName: String, filePath: String, user: String, createDate: Timestamp) {
    val createDateTime = DateUtils.toLocalDateTime(createDate)
  }

}