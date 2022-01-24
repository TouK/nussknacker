package pl.touk.nussknacker.ui.db.entity

import pl.touk.nussknacker.engine.api.process.ProcessId
import slick.lifted.{TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.NotNull

trait TagsEntityFactory extends BaseEntityFactory {

  import profile.api._

  val processesTable: LTableQuery[ProcessEntityFactory#ProcessEntity]

  class TagsEntity(tag: Tag) extends Table[TagsEntityData](tag, "tags") {
    
    def name = column[String]("name")

    def processId = column[ProcessId]("process_id", NotNull)

    def * = (name, processId) <> (TagsEntityData.apply _ tupled, TagsEntityData.unapply)

    def pk = primaryKey("pk_tag", (name, processId))

    def process = foreignKey("tag-process-fk", processId, processesTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )
  }

  val tagsTable: LTableQuery[TagsEntityFactory#TagsEntity] = LTableQuery(new TagsEntity(_))   
}

case class TagsEntityData(name: String, processId: ProcessId)
