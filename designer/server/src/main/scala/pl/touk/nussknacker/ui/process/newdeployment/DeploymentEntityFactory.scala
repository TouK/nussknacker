package pl.touk.nussknacker.ui.process.newdeployment

import pl.touk.nussknacker.engine.api.process.ProcessId
import pl.touk.nussknacker.ui.db.entity.{BaseEntityFactory, ProcessEntityData, ProcessEntityFactory}
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentEntityFactory.DeploymentEntityData
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentId
import slick.lifted.{ForeignKeyQuery, ProvenShape, TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.NotNull

import java.sql.Timestamp
import java.util.UUID

trait DeploymentEntityFactory extends BaseEntityFactory { self: ProcessEntityFactory =>

  import profile.api._

  lazy val deploymentsTable: LTableQuery[DeploymentsEntity] = TableQuery(new DeploymentsEntity(_))

  class DeploymentsEntity(tag: Tag) extends Table[DeploymentEntityData](tag, "deployments") {

    def id: Rep[DeploymentId] = column[DeploymentId]("id", O.PrimaryKey)

    // We currently need a foreign key to scenarios to fetch deployment status - it might change in the future
    def scenarioId: Rep[ProcessId] = column[ProcessId]("scenario_id", NotNull)

    def createdAt: Rep[Timestamp] = column[Timestamp]("created_at", NotNull)

    def createdBy: Rep[String] = column[String]("created_by", NotNull)

    override def * : ProvenShape[DeploymentEntityData] =
      (id, scenarioId, createdAt, createdBy) <> (DeploymentEntityData.apply _ tupled, DeploymentEntityData.unapply)

    private def scenarios_fk: ForeignKeyQuery[ProcessEntityFactory#ProcessEntity, ProcessEntityData] =
      foreignKey("deployments_scenarios_fk", scenarioId, processesTable)(
        _.id,
        onUpdate = ForeignKeyAction.Cascade,
        onDelete = ForeignKeyAction.NoAction
      )

  }

  protected implicit def deploymentIdMapping: BaseColumnType[DeploymentId] =
    MappedColumnType.base[DeploymentId, UUID](_.value, DeploymentId.apply)

}

object DeploymentEntityFactory {

  final case class DeploymentEntityData(
      id: DeploymentId,
      scenarioId: ProcessId,
      createdAt: Timestamp,
      createdBy: String
  )

}
