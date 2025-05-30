package pl.touk.nussknacker.ui.process.newdeployment

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.{DeploymentStatus, DeploymentStatusName, ProblemDeploymentStatus}
import pl.touk.nussknacker.engine.api.deployment.DeploymentStatus.{
  Canceled,
  DuringCancel,
  DuringDeploy,
  Finished,
  Restarting,
  Running
}
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.engine.newdeployment.DeploymentId
import pl.touk.nussknacker.ui.db.entity.{BaseEntityFactory, ProcessEntityData, ProcessEntityFactory}
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentEntityFactory.{
  deploymentStatusFromEntityData,
  DeploymentEntityData,
  WithModifiedAt
}
import slick.lifted.{ForeignKeyQuery, ProvenShape, TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.NotNull

import java.sql.Timestamp
import java.time.Instant

trait DeploymentEntityFactory extends BaseEntityFactory { self: ProcessEntityFactory =>

  import profile.apiWithEnforcedSchema._

  lazy val deploymentsTable: LTableQuery[DeploymentsEntity] = TableQuery(new DeploymentsEntity(_))

  class DeploymentsEntity(tag: Tag) extends TableWithSchema[DeploymentEntityData](tag, "deployments") {

    def id: Rep[DeploymentId] = column[DeploymentId]("id", O.PrimaryKey)

    // We currently need a foreign key to scenarios to fetch deployment status - it might change in the future
    def scenarioId: Rep[ProcessId] = column[ProcessId]("scenario_id", NotNull)

    def createdAt: Rep[Timestamp] = column[Timestamp]("created_at", NotNull)

    def createdBy: Rep[String] = column[String]("created_by", NotNull)

    def statusName: Rep[DeploymentStatusName] = column[DeploymentStatusName]("status_name", NotNull)

    def statusProblemDescription: Rep[Option[String]] = column[Option[String]]("status_problem_description")

    def statusModifiedAt: Rep[Timestamp] = column[Timestamp]("status_modified_at", NotNull)

    def scenarioVersionId: Rep[Option[VersionId]] = column[Option[VersionId]]("scenario_version_id")

    def startedAt: Rep[Option[Timestamp]] = column[Option[Timestamp]]("started_at")

    override def * : ProvenShape[DeploymentEntityData] =
      (
        id,
        scenarioId,
        createdAt,
        createdBy,
        statusName,
        statusProblemDescription,
        statusModifiedAt,
        scenarioVersionId,
        startedAt
      ) <> (createEntity _ tupled, extractFieldsFromEntity)

    private def scenarios_fk: ForeignKeyQuery[ProcessEntityFactory#ProcessEntity, ProcessEntityData] =
      foreignKey("deployments_scenarios_fk", scenarioId, processesTable)(
        _.id,
        onUpdate = ForeignKeyAction.Cascade,
        onDelete = ForeignKeyAction.NoAction
      )

  }

  private def createEntity(
      id: DeploymentId,
      scenarioId: ProcessId,
      createdAt: Timestamp,
      createdBy: String,
      statusName: DeploymentStatusName,
      statusProblemDescription: Option[String],
      statusModifiedAt: Timestamp,
      versionId: Option[VersionId],
      startedAt: Option[Timestamp]
  ) = {
    val status = deploymentStatusFromEntityData(
      deploymentId = id,
      name = statusName,
      description = statusProblemDescription,
      startedAt = startedAt.map(_.toInstant),
      modifiedAt = statusModifiedAt.toInstant,
      version = versionId
    )
    DeploymentEntityData(id, scenarioId, createdAt, createdBy, WithModifiedAt(status, statusModifiedAt))
  }

  private def extractFieldsFromEntity(entity: DeploymentEntityData) = {
    Option(
      entity.id,
      entity.scenarioId,
      entity.createdAt,
      entity.createdBy,
      entity.statusWithModifiedAt.value.name,
      entity.statusWithModifiedAt.value.problemDescription,
      entity.statusWithModifiedAt.modifiedAt,
      entity.statusWithModifiedAt.value.version,
      entity.statusWithModifiedAt.value.startedAt.map(Timestamp.from),
    )
  }

}

object DeploymentEntityFactory extends LazyLogging {

  def deploymentStatusFromEntityData(
      deploymentId: DeploymentId,
      name: DeploymentStatusName,
      description: Option[String],
      startedAt: Option[Instant],
      modifiedAt: Instant,
      version: Option[VersionId]
  ): DeploymentStatus = {
    name match {
      case name @ DeploymentStatusName.duringDeployStatusName =>
        DuringDeploy(scenarioVersionWithFallback(version, name, deploymentId))
      case name @ DeploymentStatusName.runningStatusName =>
        Running(scenarioVersionWithFallback(version, name, deploymentId), startedAt.getOrElse(modifiedAt))
      case name @ DeploymentStatusName.finishedStatusName =>
        Finished(scenarioVersionWithFallback(version, name, deploymentId))
      case DeploymentStatusName.restartingStatusName =>
        Restarting
      case DeploymentStatusName.duringCancelStatusName =>
        DuringCancel
      case DeploymentStatusName.canceledStatusName =>
        Canceled
      case DeploymentStatusName.problemStatusName =>
        val desc = description.getOrElse(throw new IllegalStateException("No description for ProblemDeploymentStatus"))
        ProblemDeploymentStatus(desc)
      case other =>
        throw new IllegalStateException(s"Unknown DeploymentStatusName: $other")
    }
  }

  private def scenarioVersionWithFallback(
      scenarioVersion: Option[VersionId],
      statusName: DeploymentStatusName,
      deploymentId: DeploymentId
  ) = {
    // deployments table may contain old entries without a scenario version
    scenarioVersion.getOrElse {
      logger.warn(
        s"Scenario version is missing for deployment '$deploymentId' in state '${statusName.value}'. Using scenario version -1 instead"
      )
      VersionId(-1)
    }
  }

  final case class DeploymentEntityData(
      id: DeploymentId,
      scenarioId: ProcessId,
      createdAt: Timestamp,
      createdBy: String,
      statusWithModifiedAt: WithModifiedAt[DeploymentStatus]
  )

  case class WithModifiedAt[T](value: T, modifiedAt: Timestamp)

}
