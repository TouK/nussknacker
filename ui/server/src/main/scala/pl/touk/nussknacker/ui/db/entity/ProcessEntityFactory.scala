package pl.touk.nussknacker.ui.db.entity

import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.restmodel.ProcessType
import pl.touk.nussknacker.restmodel.ProcessType.ProcessType
import slick.ast.BaseTypedType
import slick.jdbc.{JdbcProfile, JdbcType}
import slick.lifted.{TableQuery => LTableQuery} 
import slick.sql.SqlProfile.ColumnOption.NotNull

trait ProcessEntityFactory {
  protected val profile: JdbcProfile
  import profile.api._
  
  class ProcessEntity(tag: Tag) extends Table[ProcessEntityData](tag, "processes") {
    
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def name = column[String]("name", NotNull)

    def description = column[Option[String]]("description", O.Length(1000))

    def processType = column[ProcessType]("type", NotNull)

    def processCategory = column[String]("category", NotNull)

    def processingType = column[ProcessingType]("processing_type", NotNull)

    def isSubprocess = column[Boolean]("is_subprocess", NotNull)

    def isArchived = column[Boolean]("is_archived", NotNull)

    def * = (id, name, description, processType, processCategory, processingType, isSubprocess, isArchived) <> (ProcessEntityData.apply _ tupled, ProcessEntityData.unapply)

  }
  
  val processesTable: LTableQuery[ProcessEntityFactory#ProcessEntity] = LTableQuery(new ProcessEntity(_))

  implicit def processTypeMapper: JdbcType[ProcessType] with BaseTypedType[ProcessType] = MappedColumnType.base[ProcessType, String](
    _.toString,
    ProcessType.withName
  )

}

case class ProcessEntityData(id: Long,
                             name: String,
                             description: Option[String],
                             processType: ProcessType,
                             processCategory: String,
                             processingType: ProcessingType,
                             isSubprocess: Boolean,
                             isArchived: Boolean)


