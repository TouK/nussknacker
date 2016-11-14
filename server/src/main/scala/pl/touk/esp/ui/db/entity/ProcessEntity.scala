package pl.touk.esp.ui.db.entity

import db.migration.DefaultJdbcProfile.profile.api._
import pl.touk.esp.ui.db.entity.ProcessEntity.ProcessType.ProcessType
import slick.sql.SqlProfile.ColumnOption.NotNull

object ProcessEntity {

  implicit def processTypeMapper = MappedColumnType.base[ProcessType, String](
    _.toString,
    ProcessType.withName
  )

  class ProcessEntity(tag: Tag) extends Table[ProcessEntityData](tag, "processes") {

    def id = column[String]("id", O.PrimaryKey)

    def name = column[String]("name", NotNull)

    def description = column[Option[String]]("description", O.Length(1000))

    def processType = column[ProcessType]("type", NotNull)

    def processCategory = column[String]("category", NotNull)

    def * = (id, name, description, processType, processCategory) <> (ProcessEntityData.apply _ tupled, ProcessEntityData.unapply)

  }

  case class ProcessEntityData(id: String,
                               name: String,
                               description: Option[String],
                               processType: ProcessType,
                               processCategory: String
                              )

  object ProcessType extends Enumeration {
    type ProcessType = Value
    val Graph = Value("graph")
    val Custom = Value("custom")
  }

}

