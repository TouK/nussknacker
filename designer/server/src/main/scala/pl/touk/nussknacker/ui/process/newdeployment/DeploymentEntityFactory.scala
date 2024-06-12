package pl.touk.nussknacker.ui.process.newdeployment

import pl.touk.nussknacker.engine.api.deployment.{DeploymentStatus, DeploymentStatusName, ProblemDeploymentStatus}
import pl.touk.nussknacker.engine.api.process.ProcessId
import pl.touk.nussknacker.engine.newdeployment.DeploymentId
import pl.touk.nussknacker.ui.db.entity.{BaseEntityFactory, ProcessEntityData, ProcessEntityFactory}
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentEntityFactory.{DeploymentEntityData, WithModifiedAt}
import slick.lifted.{ForeignKeyQuery, ProvenShape, TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.NotNull

import java.sql.Timestamp

trait DeploymentEntityFactory extends BaseEntityFactory { self: ProcessEntityFactory =>

  import profile.api._

  lazy val deploymentsTable: LTableQuery[DeploymentsEntity] = TableQuery(new DeploymentsEntity(_))

  class DeploymentsEntity(tag: Tag) extends Table[DeploymentEntityData](tag, "deployments") {

    def id: Rep[DeploymentId] = column[DeploymentId]("id", O.PrimaryKey)

    // We currently need a foreign key to scenarios to fetch deployment status - it might change in the future
    def scenarioId: Rep[ProcessId] = column[ProcessId]("scenario_id", NotNull)

    def createdAt: Rep[Timestamp] = column[Timestamp]("created_at", NotNull)

    def createdBy: Rep[String] = column[String]("created_by", NotNull)

    def statusName: Rep[DeploymentStatusName] = column[DeploymentStatusName]("status_name", NotNull)

    def statusProblemDescription: Rep[Option[String]] = column[Option[String]]("status_problem_description")

    def statusModifiedAt: Rep[Timestamp] = column[Timestamp]("status_modified_at", NotNull)

    override def * : ProvenShape[DeploymentEntityData] =
      (
        id,
        scenarioId,
        createdAt,
        createdBy,
        statusName,
        statusProblemDescription,
        statusModifiedAt
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
      statusModifiedAt: Timestamp
  ) = {
    val status = if (statusName == ProblemDeploymentStatus.name) {
      ProblemDeploymentStatus(
        statusProblemDescription.getOrElse(throw new IllegalStateException("Problem status without description"))
      )
    } else {
      DeploymentStatus.withName(statusName.value)
    }
    DeploymentEntityData(id, scenarioId, createdAt, createdBy, WithModifiedAt(status, statusModifiedAt))
  }

  private def extractFieldsFromEntity(entity: DeploymentEntityData) = {
    val statusProblemDescription = ProblemDeploymentStatus.extractDescription(entity.statusWithModifiedAt.value)
    Option(
      entity.id,
      entity.scenarioId,
      entity.createdAt,
      entity.createdBy,
      entity.statusWithModifiedAt.value.name,
      statusProblemDescription,
      entity.statusWithModifiedAt.modifiedAt
    )
  }

}

object DeploymentEntityFactory {

  final case class DeploymentEntityData(
      id: DeploymentId,
      scenarioId: ProcessId,
      createdAt: Timestamp,
      createdBy: String,
      statusWithModifiedAt: WithModifiedAt[DeploymentStatus]
  )

  case class WithModifiedAt[T](value: T, modifiedAt: Timestamp)

}
