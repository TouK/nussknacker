package pl.touk.nussknacker.ui.db.entity

import db.migration.DefaultJdbcProfile.profile.api._
import pl.touk.nussknacker.ui.db.EspTables
import slick.sql.SqlProfile.ColumnOption.NotNull

object TagsEntity {

  class TagsEntity(tag: Tag) extends Table[TagsEntityData](tag, "tags") {

    def name = column[String]("name")

    def processId = column[Long]("process_id", NotNull)

    def * = (name, processId) <> (TagsEntityData.apply _ tupled, TagsEntityData.unapply)

    def pk = primaryKey("pk_tag", (name, processId))

    def process = foreignKey("tag-process-fk", processId, EspTables.processesTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )
  }

  case class TagsEntityData(name: String, processId: Long)

}

