package pl.touk.esp.ui.db.migration

import java.sql.Timestamp

import pl.touk.esp.ui.db.migration.CreateDeployedProcessesMigration.DeployedProcessVersionEntityData
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
    deployedProcessesTable += DeployedProcessVersionEntityData(
      process.id,
      1,
      "test",
      "TouK",
      DateUtils.now
    )
  }

  val deployedProcessesTable = TableQuery[DeployedProcessVersionEntity]

  class DeployedProcessVersionEntity(tag: Tag) extends Table[DeployedProcessVersionEntityData](tag, "deployed_process_versions") {

    def processId = column[String]("process_id", NotNull)

    def processVersionId = column[Long]("process_version_id", NotNull)

    def deployedAt = column[Timestamp]("deploy_at", NotNull)

    def environment = column[String]("environment", NotNull)

    def user = column[String]("user", NotNull)

    def pk = primaryKey("pk_deployed_process_version", (processId, processVersionId, environment, deployedAt))

    //fixme czy naprwade musze tak dziwnie to wszedzie robic?
    val processVersionsMigration = new CreateProcessVersionsMigration {
      override protected val profile: JdbcProfile = CreateDeployedProcessesMigration.this.profile
    }
    val environmentsMigration = new CreateEnvironmentsMigration {
      override protected val profile: JdbcProfile = CreateDeployedProcessesMigration.this.profile
    }

    import processVersionsMigration._
    import environmentsMigration._

    def processes_fk = foreignKey("proc_ver_in_deployed_proc_fk", (processId, processVersionId), processVersionsTable)(
      (procV) => (procV.processId, procV.id),
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.NoAction
    )

    def environment_fk = foreignKey("env_in_deployed_proc_fk", environment, environmentsTable)(
      _.name,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.NoAction
    )

    def * = (processId, processVersionId, environment, user, deployedAt) <> (DeployedProcessVersionEntityData.tupled, DeployedProcessVersionEntityData.unapply)

  }

}

object CreateDeployedProcessesMigration {
  //moze dodac hasha/wersje z gita? sbt-buildinfo + sbt-git sie nada https://github.com/sbt/sbt-git/issues/33
  case class DeployedProcessVersionEntityData(processId: String, processVersionId: Long, environment: String, user: String, deployedAt: Timestamp) {
    val deployedAtTime = DateUtils.toLocalDateTime(deployedAt)
  }

}