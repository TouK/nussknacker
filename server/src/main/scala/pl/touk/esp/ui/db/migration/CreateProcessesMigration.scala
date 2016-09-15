package pl.touk.esp.ui.db.migration

import argonaut.PrettyParams
import pl.touk.esp.engine.marshall.ProcessMarshaller
import pl.touk.esp.ui.db.migration.CreateProcessesMigration.{ProcessType, ProcessEntityData}
import pl.touk.esp.ui.db.migration.CreateProcessesMigration.ProcessType.ProcessType

import pl.touk.esp.ui.sample.SampleProcess
import slick.sql.SqlProfile.ColumnOption.NotNull

trait CreateProcessesMigration extends SlickMigration {

  import profile.api._

  implicit def processTypeMapper = MappedColumnType.base[ProcessType, String](
    _.toString,
    ProcessType.withName
  )

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
      ProcessType.Graph,
      Some(json), None
    )
  }

  val processesTable = TableQuery[ProcessEntity]

  class ProcessEntity(tag: Tag) extends Table[ProcessEntityData](tag, "processes") {

    def id = column[String]("id", O.PrimaryKey)

    def name = column[String]("name", NotNull)

    def description = column[Option[String]]("description", O.Length(1000))

    def processType = column[ProcessType]("type", NotNull)

    def json = column[Option[String]]("json", O.Length(100 * 1000))

    def mainClass = column[Option[String]]("mainClass", O.Length(5000))

    def * = (id, name, description, processType, json, mainClass) <> (ProcessEntityData.apply _ tupled, ProcessEntityData.unapply)

  }

}

object CreateProcessesMigration {

  case class ProcessEntityData(id: String, name: String, description: Option[String],
                               processType: ProcessType,
                               json: Option[String], mainClass: Option[String])

  object ProcessType extends Enumeration {
    type ProcessType = Value
    val Graph = Value("graph")
    val Custom = Value("custom")
  }

}