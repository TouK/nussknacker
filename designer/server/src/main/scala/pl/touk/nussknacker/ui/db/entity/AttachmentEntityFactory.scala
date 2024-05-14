package pl.touk.nussknacker.ui.db.entity

import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import slick.ast.ColumnOption.PrimaryKey
import slick.lifted.{TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.NotNull

import java.sql.Timestamp
import java.time.Instant

trait AttachmentEntityFactory extends BaseEntityFactory {

  import profile.api._

  class AttachmentEntity(tag: Tag) extends Table[AttachmentEntityData](tag, "process_attachments") {

    def id = column[Long]("id", PrimaryKey, O.AutoInc)

    def processId = column[ProcessId]("process_id", NotNull)

    def processVersionId = column[VersionId]("process_version_id", NotNull)

    def fileName = column[String]("file_name", NotNull)

    def data = column[Array[Byte]]("data", NotNull)

    def createDate = column[Timestamp]("create_date", NotNull)

    def user = column[String]("user", NotNull)

    def impersonatedBy = column[Option[String]]("impersonated_by")

    def * = (id, processId, processVersionId, fileName, data, user, impersonatedBy, createDate) <> (
      AttachmentEntityData.apply _ tupled, AttachmentEntityData.unapply
    )

  }

  val attachmentsTable: LTableQuery[AttachmentEntityFactory#AttachmentEntity] = LTableQuery(new AttachmentEntity(_))

}

final case class AttachmentEntityData(
    id: Long,
    processId: ProcessId,
    processVersionId: VersionId,
    fileName: String,
    data: Array[Byte],
    user: String,
    impersonatedBy: Option[String],
    createDate: Timestamp
) {
  val createDateTime: Instant = createDate.toInstant
}
