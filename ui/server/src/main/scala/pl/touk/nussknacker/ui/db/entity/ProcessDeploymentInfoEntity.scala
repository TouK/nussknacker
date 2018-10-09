package pl.touk.nussknacker.ui.db.entity

import java.sql.Timestamp

import db.migration.DefaultJdbcProfile.profile.api._
import pl.touk.nussknacker.ui.db.EspTables
import pl.touk.nussknacker.ui.db.entity.ProcessDeploymentInfoEntity.DeploymentAction.DeploymentAction
import pl.touk.nussknacker.ui.util.DateUtils
import slick.sql.SqlProfile.ColumnOption.{NotNull, Nullable}

object ProcessDeploymentInfoEntity {

  implicit def processTypeMapper = MappedColumnType.base[DeploymentAction, String](
    _.toString,
    DeploymentAction.withName
  )


  class ProcessDeploymentInfoEntity(tag: Tag) extends Table[DeployedProcessVersionEntityData](tag, "process_deployment_info") {

    def processId = column[Long]("process_id", NotNull)

    def processVersionId = column[Option[Long]]("process_version_id", Nullable)

    def deployedAt = column[Timestamp]("deploy_at", NotNull)

    def environment = column[String]("environment", NotNull)

    def user = column[String]("user", NotNull)

    def buildInfo = column[Option[String]]("build_info", Nullable)

    def deploymentAction = column[DeploymentAction]("deployment_action", NotNull)

    def pk = primaryKey("pk_deployed_process_version", (processId, processVersionId, environment, deployedAt))

    def processes_fk = foreignKey("proc_ver_in_deployed_proc_fk", (processId, processVersionId), EspTables.processVersionsTable)(
      (procV) => (procV.processId, procV.id.?),
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.NoAction
    )

    def environment_fk = foreignKey("env_in_deployed_proc_fk", environment, EspTables.environmentsTable)(
      _.name,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.NoAction
    )

    def * = (processId, processVersionId, environment, user, deployedAt, deploymentAction, buildInfo) <> (DeployedProcessVersionEntityData.tupled, DeployedProcessVersionEntityData.unapply)

  }

  case class DeployedProcessVersionEntityData(processId: Long,
                                              processVersionId: Option[Long],
                                              environment: String,
                                              user: String,
                                              deployedAt: Timestamp,
                                              deploymentAction: DeploymentAction,
                                              buildInfo: Option[String]) {
    val deployedAtTime = DateUtils.toLocalDateTime(deployedAt)
  }

  object DeploymentAction extends Enumeration {
    type DeploymentAction = Value
    val Deploy = Value("DEPLOY")
    val Cancel = Value("CANCEL")
  }


}

