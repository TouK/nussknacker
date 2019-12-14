package pl.touk.nussknacker.ui.db.entity

import java.sql.Timestamp
import java.time.LocalDateTime

import pl.touk.nussknacker.restmodel.processdetails.DeploymentAction
import pl.touk.nussknacker.restmodel.processdetails.DeploymentAction.DeploymentAction
import pl.touk.nussknacker.ui.util.DateUtils
import slick.ast.BaseTypedType
import slick.jdbc.{JdbcProfile, JdbcType}
import slick.lifted.{ForeignKeyQuery, ProvenShape, TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.{NotNull, Nullable}

trait ProcessDeploymentInfoEntityFactory {

  protected val profile: JdbcProfile
  import profile.api._

  implicit def deploymentMapper: JdbcType[DeploymentAction] with BaseTypedType[DeploymentAction] =
    MappedColumnType.base[DeploymentAction, String](_.toString, DeploymentAction.withName)

  val deployedProcessesTable: LTableQuery[ProcessDeploymentInfoEntityFactory#ProcessDeploymentInfoEntity] =
    LTableQuery(new ProcessDeploymentInfoEntity(_))

  val processVersionsTable: LTableQuery[ProcessVersionEntityFactory#ProcessVersionEntity]
  val commentsTable: LTableQuery[CommentEntityFactory#CommentEntity]
  val environmentsTable: LTableQuery[EnvironmentsEntityFactory#EnvironmentsEntity]

  class ProcessDeploymentInfoEntity(tag: Tag) extends Table[DeployedProcessInfoEntityData](tag, "process_deployment_info") {
    def processId: Rep[Long] = column[Long]("process_id", NotNull)

    def processVersionId: Rep[Long] = column[Long]("process_version_id", Nullable)

    def deployedAt: Rep[Timestamp] = column[Timestamp]("deploy_at", NotNull)

    def environment: Rep[String] = column[String]("environment", NotNull)

    def user: Rep[String] = column[String]("user", NotNull)

    def buildInfo: Rep[Option[String]] = column[Option[String]]("build_info", Nullable)

    def deploymentAction: Rep[DeploymentAction] = column[DeploymentAction]("deployment_action", NotNull)

    def commentId: Rep[Option[Long]] = column[Option[Long]]("comment_id", Nullable)

    def pk = primaryKey("pk_deployed_process_version", (processId, processVersionId, environment, deployedAt))

    def processes_fk: ForeignKeyQuery[ProcessVersionEntityFactory#ProcessVersionEntity, ProcessVersionEntityData] = foreignKey("proc_ver_in_deployed_proc_fk", (processId, processVersionId), processVersionsTable)(
      procV => (procV.processId, procV.id),
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.NoAction
    )

    def comment_fk: ForeignKeyQuery[CommentEntityFactory#CommentEntity, CommentEntityData] = foreignKey("comment_in_deployed_proc_fk", commentId, commentsTable)(
      _.id.?,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.SetNull
    )

    def environment_fk: ForeignKeyQuery[EnvironmentsEntityFactory#EnvironmentsEntity, EnvironmentsEntityData] = foreignKey("env_in_deployed_proc_fk", environment, environmentsTable)(
      _.name,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.NoAction
    )

    def * : ProvenShape[DeployedProcessInfoEntityData] = (processId, processVersionId, environment, user, deployedAt, deploymentAction, commentId, buildInfo) <> (
      DeployedProcessInfoEntityData.tupled, DeployedProcessInfoEntityData.unapply)
  }
}

case class DeployedProcessInfoEntityData(processId: Long,
                                         processVersionId: Long,
                                         environment: String,
                                         user: String,
                                         deployedAt: Timestamp,
                                         deploymentAction: DeploymentAction,
                                         commentId: Option[Long],
                                         buildInfo: Option[String]) {

  lazy val deployedAtTime: LocalDateTime = DateUtils.toLocalDateTime(deployedAt)
  lazy val isDeployed = deploymentAction.equals(DeploymentAction.Deploy)
  lazy val isCanceled = deploymentAction.equals(DeploymentAction.Cancel)
}