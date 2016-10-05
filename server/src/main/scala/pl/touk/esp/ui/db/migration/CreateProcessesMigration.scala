package pl.touk.esp.ui.db.migration

import java.sql.Timestamp

import argonaut.PrettyParams
import pl.touk.esp.engine.marshall.ProcessMarshaller
import pl.touk.esp.ui.db.migration.CreateProcessesMigration.{ProcessEntityData, ProcessType, ProcessVersionEntityData}
import pl.touk.esp.ui.db.migration.CreateProcessesMigration.ProcessType.ProcessType
import pl.touk.esp.ui.sample.SampleProcess
import pl.touk.esp.ui.util.DateUtils
import slick.jdbc.JdbcProfile
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
    processesTable += ProcessEntityData(
      process.id,
      process.id,
      Some("Sample process description"),
      ProcessType.Graph
    )
  }

  val processesTable = TableQuery[ProcessEntity]

  class ProcessEntity(tag: Tag) extends Table[ProcessEntityData](tag, "processes") {

    def id = column[String]("id", O.PrimaryKey)

    def name = column[String]("name", NotNull)

    def description = column[Option[String]]("description", O.Length(1000))

    def processType = column[ProcessType]("type", NotNull)

    def * = (id, name, description, processType) <> (ProcessEntityData.apply _ tupled, ProcessEntityData.unapply)

  }

}

trait CreateProcessVersionsMigration extends SlickMigration { self =>
  import profile.api._

  private val processesMigration = new CreateProcessesMigration {
    override protected val profile: JdbcProfile = CreateProcessVersionsMigration.this.profile
  }

  import processesMigration._

  val processVersionsTable = TableQuery[ProcessVersionEntity]


  override def migrateActions = {
    processVersionsTable.schema.create andThen
      populateWithSample
  }

  private def populateWithSample = {
    val process = SampleProcess.process
    val json = ProcessMarshaller.toJson(process, PrettyParams.nospace)
    processVersionsTable += ProcessVersionEntityData(
      1,
      process.id,
      Some(json),
      None,
      DateUtils.now,
      "TouK"
    )

  }

  class ProcessVersionEntity(tag: Tag) extends Table[ProcessVersionEntityData](tag, "process_versions") {
    def id = column[Long]("id", NotNull)

    def json = column[Option[String]]("json", O.Length(100 * 1000))

    def mainClass = column[Option[String]]("main_class", O.Length(5000))

    def createDate = column[Timestamp]("create_date", NotNull)

    def user = column[String]("user", NotNull)

    def processId = column[String]("process_id", NotNull)

    def * = (id, processId, json, mainClass, createDate, user) <> (ProcessVersionEntityData.apply _ tupled, ProcessVersionEntityData.unapply)

    def pk = primaryKey("pk_process_version", (processId, id))

    private def process = foreignKey("process-version-process-fk", processId, processesTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )
  }

}

object CreateProcessesMigration {

  case class ProcessEntityData(id: String,
                               name: String,
                               description: Option[String],
                               processType: ProcessType
                              )

  case class ProcessVersionEntityData(
                                       id: Long,
                                       processId: String,
                                       json: Option[String],
                                       mainClass: Option[String],
                                       createDate: Timestamp,
                                       user: String
                                     )

  object ProcessType extends Enumeration {
    type ProcessType = Value
    val Graph = Value("graph")
    val Custom = Value("custom")
  }

}