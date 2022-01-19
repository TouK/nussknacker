package pl.touk.nussknacker.ui.db.entity

import pl.touk.nussknacker.restmodel.process.ProcessingType
import slick.jdbc.JdbcProfile
import slick.lifted.{ProvenShape, TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.NotNull

import java.sql.Timestamp

trait ProcessEntityFactory {

  protected val profile: JdbcProfile
  import profile.api._

  val processesTable: LTableQuery[ProcessEntityFactory#ProcessEntity] = LTableQuery(new ProcessEntity(_))

  class ProcessEntity(tag: Tag) extends Table[ProcessEntityData](tag, "processes") {
    
    def id: Rep[Long] = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def name: Rep[String] = column[String]("name", NotNull)

    def description: Rep[Option[String]] = column[Option[String]]("description", O.Length(1000))

    def processCategory: Rep[String] = column[String]("category", NotNull)

    def processingType: Rep[ProcessingType] = column[ProcessingType]("processing_type", NotNull)

    def isSubprocess: Rep[Boolean] = column[Boolean]("is_subprocess", NotNull)

    def isArchived: Rep[Boolean] = column[Boolean]("is_archived", NotNull)

    def createdAt: Rep[Timestamp] = column[Timestamp]("created_at", NotNull)

    def createdBy: Rep[String] = column[String]("created_by", NotNull)

    def * : ProvenShape[ProcessEntityData] = (id, name, description, processCategory, processingType, isSubprocess, isArchived, createdAt, createdBy) <> (ProcessEntityData.apply _ tupled, ProcessEntityData.unapply)
  }
}

case class ProcessEntityData(id: Long,
                             name: String,
                             description: Option[String],
                             processCategory: String,
                             processingType: ProcessingType,
                             isSubprocess: Boolean,
                             isArchived: Boolean,
                             createdAt: Timestamp,
                             createdBy: String)
