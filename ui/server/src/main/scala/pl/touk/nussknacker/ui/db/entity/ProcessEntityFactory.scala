package pl.touk.nussknacker.ui.db.entity

import java.sql.Timestamp

import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.restmodel.ProcessType
import pl.touk.nussknacker.restmodel.ProcessType.ProcessType
import slick.ast.BaseTypedType
import slick.jdbc.{JdbcProfile, JdbcType}
import slick.lifted.{ProvenShape, TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.NotNull

trait ProcessEntityFactory {

  protected val profile: JdbcProfile
  import profile.api._

  implicit def processTypeMapper: JdbcType[ProcessType] with BaseTypedType[ProcessType] =
    MappedColumnType.base[ProcessType, String](_.toString, ProcessType.withName)

  val processesTable: LTableQuery[ProcessEntityFactory#ProcessEntity] = LTableQuery(new ProcessEntity(_))

  class ProcessEntity(tag: Tag) extends Table[ProcessEntityData](tag, "processes") {
    
    def id: Rep[Long] = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def name: Rep[String] = column[String]("name", NotNull)

    def description: Rep[Option[String]] = column[Option[String]]("description", O.Length(1000))

    def processType: Rep[ProcessType] = column[ProcessType]("type", NotNull)

    def processCategory: Rep[String] = column[String]("category", NotNull)

    def processingType: Rep[ProcessingType] = column[ProcessingType]("processing_type", NotNull)

    def isSubprocess: Rep[Boolean] = column[Boolean]("is_subprocess", NotNull)

    def isArchived: Rep[Boolean] = column[Boolean]("is_archived", NotNull)

    def createdAt: Rep[Timestamp] = column[Timestamp]("created_at", NotNull)

    def createdBy: Rep[String] = column[String]("created_by", NotNull)

    def * : ProvenShape[ProcessEntityData] = (id, name, description, processType, processCategory, processingType, isSubprocess, isArchived, createdAt, createdBy) <> (ProcessEntityData.apply _ tupled, ProcessEntityData.unapply)
  }
}

case class ProcessEntityData(id: Long,
                             name: String,
                             description: Option[String],
                             processType: ProcessType,
                             processCategory: String,
                             processingType: ProcessingType,
                             isSubprocess: Boolean,
                             isArchived: Boolean,
                             createdAt: Timestamp,
                             createdBy: String)
