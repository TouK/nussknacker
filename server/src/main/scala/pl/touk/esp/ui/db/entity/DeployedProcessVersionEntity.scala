package pl.touk.esp.ui.db.entity

import java.sql.Timestamp

import db.migration.DefaultJdbcProfile.profile.api._
import pl.touk.esp.ui.db.EspTables
import pl.touk.esp.ui.util.DateUtils
import slick.sql.SqlProfile.ColumnOption.{NotNull, Nullable}

object DeployedProcessVersionEntity {

  class DeployedProcessVersionEntity(tag: Tag) extends Table[DeployedProcessVersionEntityData](tag, "deployed_process_versions") {

    def processId = column[String]("process_id", NotNull)

    def processVersionId = column[Long]("process_version_id", NotNull)

    def deployedAt = column[Timestamp]("deploy_at", NotNull)

    def environment = column[String]("environment", NotNull)

    def user = column[String]("user", NotNull)

    def buildInfo = column[Option[String]]("build_info", Nullable)

    def pk = primaryKey("pk_deployed_process_version", (processId, processVersionId, environment, deployedAt))

    def processes_fk = foreignKey("proc_ver_in_deployed_proc_fk", (processId, processVersionId), EspTables.processVersionsTable)(
      (procV) => (procV.processId, procV.id),
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.NoAction
    )

    def environment_fk = foreignKey("env_in_deployed_proc_fk", environment, EspTables.environmentsTable)(
      _.name,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.NoAction
    )

    def * = (processId, processVersionId, environment, user, deployedAt, buildInfo) <> (DeployedProcessVersionEntityData.tupled, DeployedProcessVersionEntityData.unapply)

  }

  case class DeployedProcessVersionEntityData(processId: String,
                                              processVersionId: Long,
                                              environment: String,
                                              user: String,
                                              deployedAt: Timestamp,
                                              buildInfo: Option[String]) {
    val deployedAtTime = DateUtils.toLocalDateTime(deployedAt)
  }


}

