package pl.touk.esp.ui.db.migration

import pl.touk.esp.ui.db.migration.CreateTagsMigration.TagsEntityData
import pl.touk.esp.ui.sample.SampleProcess
import slick.jdbc.JdbcProfile
import slick.sql.SqlProfile.ColumnOption.NotNull

trait CreateTagsMigration extends SlickMigration {

  import profile.api._

  override def migrateActions = {
    tagsTable.schema.create andThen
      populateWithSample
  }

  private def populateWithSample = {
    val process = SampleProcess.process
    tagsTable ++= Seq(
      TagsEntityData(
        name = "on_prod",
        processId = process.id
      ),
      TagsEntityData(
        name = "fraud",
        processId = process.id
      )
    )
  }

  val tagsTable = TableQuery[TagsEntity]

  lazy val processesMigration = new CreateProcessesMigration {
    override protected val profile: JdbcProfile = CreateTagsMigration.this.profile
  }
  import processesMigration._

  class TagsEntity(tag: Tag) extends Table[TagsEntityData](tag, "tags") {

    def name = column[String]("name")

    def processId = column[String]("process_id", NotNull)

    def * = (name, processId) <> (TagsEntityData.apply _ tupled, TagsEntityData.unapply)

    def pk = primaryKey("pk_tag", (name, processId))

    def process = foreignKey("process-fk", processId, processesTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )

  }

}

object CreateTagsMigration {

  case class TagsEntityData(name: String, processId: String)

}