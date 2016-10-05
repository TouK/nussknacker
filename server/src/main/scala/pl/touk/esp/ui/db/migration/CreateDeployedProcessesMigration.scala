package pl.touk.esp.ui.db.migration

import java.sql.Timestamp

import argonaut.PrettyParams
import pl.touk.esp.engine.marshall.ProcessMarshaller
import pl.touk.esp.ui.db.migration.CreateDeployedProcessesMigration.DeployedProcessEntityData
import pl.touk.esp.ui.sample.SampleProcess
import pl.touk.esp.ui.util.DateUtils
import slick.jdbc.JdbcProfile
import slick.sql.SqlProfile.ColumnOption.NotNull

trait CreateDeployedProcessesMigration extends SlickMigration {

  import profile.api._

  override def migrateActions = {
    deployedProcessesTable.schema.create andThen createSample
  }

  private def createSample = {
    val process = SampleProcess.process
    val json = ProcessMarshaller.toJson(process, PrettyParams.nospace)
    deployedProcessesTable += DeployedProcessEntityData(
      process.id,
      DateUtils.now,
      json
    )
  }

  val deployedProcessesTable = TableQuery[DeployedProcessEntity]

  class DeployedProcessEntity(tag: Tag) extends Table[DeployedProcessEntityData](tag, "deployed_processes") {

    def id = column[String]("id", NotNull)

    def deployedAt = column[Timestamp]("deploy_at", NotNull)

    def json = column[String]("json", O.Length(100 * 1000), NotNull)

    def pk = primaryKey("pk_deployed_process", (id, deployedAt))

    //fixme czy naprwade musze tak dziwnie to wszedzie robic?
    val processesMigration = new CreateProcessesMigration {
      override protected val profile: JdbcProfile = CreateDeployedProcessesMigration.this.profile
    }

    import processesMigration._

    def processes_fk = foreignKey("proc_in_deployed_proc_fk", id, processesTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.NoAction
    )

    def * = (id, deployedAt, json) <> (DeployedProcessEntityData.tupled, DeployedProcessEntityData.unapply)

  }

}

object CreateDeployedProcessesMigration {
  //moze dodac hasha/wersje z gita? sbt-buildinfo + sbt-git sie nada https://github.com/sbt/sbt-git/issues/33
  case class DeployedProcessEntityData(id: String, deployedAt: Timestamp, json: String) {
    val deployedAtTime = DateUtils.toLocalDateTime(deployedAt)
  }

}