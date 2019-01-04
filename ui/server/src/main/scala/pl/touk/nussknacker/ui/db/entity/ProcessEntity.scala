package pl.touk.nussknacker.ui.db.entity

import db.migration.DefaultJdbcProfile.profile.api._
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.restmodel.ProcessType
import pl.touk.nussknacker.restmodel.ProcessType.ProcessType
import slick.sql.SqlProfile.ColumnOption.NotNull

object ProcessEntity {

  implicit def processTypeMapper = MappedColumnType.base[ProcessType, String](
    _.toString,
    ProcessType.withName
  )

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

  case class ProcessEntityData(id: Long,
                               name: String,
                               description: Option[String],
                               processType: ProcessType,
                               processCategory: String,
                               processingType: ProcessingType,
                               isSubprocess: Boolean,
                               isArchived:Boolean
                              )

}

