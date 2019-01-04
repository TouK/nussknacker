package pl.touk.nussknacker.ui.db.entity

import java.sql.Timestamp
import java.time.LocalDateTime

import db.migration.DefaultJdbcProfile.profile.api._
import pl.touk.nussknacker.restmodel.processdetails.DeploymentAction
import pl.touk.nussknacker.restmodel.processdetails.DeploymentAction.DeploymentAction
import pl.touk.nussknacker.ui.db.EspTables
import pl.touk.nussknacker.ui.util.DateUtils
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType
import slick.sql.SqlProfile.ColumnOption.{NotNull, Nullable}

object ProcessDeploymentInfoEntity {

  implicit def deploymentMapper: JdbcType[DeploymentAction] with BaseTypedType[DeploymentAction] = MappedColumnType.base[DeploymentAction, String](
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

    def commentId = column[Option[Long]]("comment_id", Nullable)

    def pk = primaryKey("pk_deployed_process_version", (processId, processVersionId, environment, deployedAt))

    def processes_fk = foreignKey("proc_ver_in_deployed_proc_fk", (processId, processVersionId), EspTables.processVersionsTable)(
      procV => (procV.processId, procV.id.?),
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.NoAction
    )

    def comment_fk = foreignKey("comment_in_deployed_proc_fk", (commentId), EspTables.commentsTable)(
      _.id.?,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.SetNull
    )

    def environment_fk = foreignKey("env_in_deployed_proc_fk", environment, EspTables.environmentsTable)(
      _.name,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.NoAction
    )

    def * = (processId, processVersionId, environment, user, deployedAt, deploymentAction, commentId, buildInfo) <> (
      DeployedProcessVersionEntityData.tupled, DeployedProcessVersionEntityData.unapply)

  }

  case class DeployedProcessVersionEntityData(processId: Long,
                                              processVersionId: Option[Long],
                                              environment: String,
                                              user: String,
                                              deployedAt: Timestamp,
                                              deploymentAction: DeploymentAction,
                                              commentId: Option[Long],
                                              buildInfo: Option[String]) {
    val deployedAtTime: LocalDateTime = DateUtils.toLocalDateTime(deployedAt)
  }

}

