package pl.touk.nussknacker.ui.db.entity

import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.ui.db.DateUtils

import java.sql.Timestamp
import java.time.LocalDateTime
import slick.ast.ColumnOption.PrimaryKey
import slick.jdbc.JdbcProfile
import slick.lifted.{TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.NotNull

trait AttachmentEntityFactory {

  protected val profile: JdbcProfile
  import profile.api._
  val processesTable: LTableQuery[ProcessEntityFactory#ProcessEntity]
  
  class AttachmentEntity(tag: Tag) extends Table[AttachmentEntityData](tag, "process_attachments") {
    
    def id = column[Long]("id", PrimaryKey)

    def processId = column[Long]("process_id", NotNull)

    def processVersionId = column[Long]("process_version_id", NotNull)

    def fileName = column[String]("file_name", NotNull)

    def filePath = column[String]("file_path", NotNull)

    def createDate = column[Timestamp]("create_date", NotNull)

    def user = column[String]("user", NotNull)

    def * = (id, processId, processVersionId, fileName, filePath, user, createDate) <> (
      (AttachmentEntityData.create _).tupled,
      (e: AttachmentEntityData) => AttachmentEntityData.unapply(e).map { t => (t._1, t._2.value, t._3.value, t._4, t._5, t._6, t._7) }
    )

  }

  val attachmentsTable: LTableQuery[AttachmentEntityFactory#AttachmentEntity] = LTableQuery(new AttachmentEntity(_)) 
  
}

object AttachmentEntityData {
  def create(id: Long, processId: Long, processVersionId: Long, fileName: String, filePath: String, user: String, createDate: Timestamp): AttachmentEntityData =
    AttachmentEntityData(id, ProcessId(processVersionId), VersionId(processId), fileName, filePath, user, createDate)
}

case class AttachmentEntityData(id: Long, processId: ProcessId, processVersionId: VersionId, fileName: String, filePath: String, user: String, createDate: Timestamp) {
  val createDateTime: LocalDateTime = DateUtils.toLocalDateTime(createDate)
}
