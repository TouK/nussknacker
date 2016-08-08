package pl.touk.esp.ui.db.migration

import argonaut.PrettyParams
import pl.touk.esp.engine.marshall.ProcessMarshaller
import pl.touk.esp.ui.db.migration.CreateProcessesMigration.ProcessEntityData
import pl.touk.esp.ui.sample.SampleProcess
import slick.profile.SqlProfile.ColumnOption.NotNull

trait CreateProcessesMigration extends SlickMigration {

  import driver.api._

  override def migrateActions = {
    processesTable.schema.create andThen
      populateWithSample
  }

  private def populateWithSample = {
    val process = SampleProcess.process
    val json = ProcessMarshaller.toJson(process, PrettyParams.nospace)
    processesTable += ProcessEntityData(
      process.id,
      "Sample process",
      Some("Sample process description"),
      Some(json)
    )
  }

  val processesTable = TableQuery[ProcessEntity]

  class ProcessEntity(tag: Tag) extends Table[ProcessEntityData](tag, "processes") {

    def id = column[String]("id", O.PrimaryKey)

    def name = column[String]("name", NotNull)

    def description = column[Option[String]]("description", O.Length(1000))

    def json = column[Option[String]]("json", O.Length(100 * 1000))

    def * = (id, name, description, json) <> (ProcessEntityData.apply _ tupled, ProcessEntityData.unapply)

  }

}

object CreateProcessesMigration {

  case class ProcessEntityData(id: String, name: String, description: Option[String], json: Option[String])

}