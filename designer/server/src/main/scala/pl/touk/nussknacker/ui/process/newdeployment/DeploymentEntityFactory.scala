package pl.touk.nussknacker.ui.process.newdeployment

import pl.touk.nussknacker.engine.api.process.ProcessId
import pl.touk.nussknacker.ui.db.entity.{BaseEntityFactory, ProcessEntityData, ProcessEntityFactory}
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentEntityFactory.DeploymentEntityData
import slick.lifted.{ForeignKeyQuery, ProvenShape, TableQuery => LTableQuery}

import java.util.UUID

trait DeploymentEntityFactory extends BaseEntityFactory { self: ProcessEntityFactory =>

  import profile.api._

  lazy val deploymentsTable: LTableQuery[DeploymentsEntity] = TableQuery(new DeploymentsEntity(_))

  class DeploymentsEntity(tag: Tag) extends Table[DeploymentEntityData](tag, "deployments") {

    def id: Rep[DeploymentIdNG] = column[DeploymentIdNG]("id", O.PrimaryKey)

    // We currently need a foreign key to scenarios to fetch deployment status - it might change in the future
    def scenarioId: Rep[ProcessId] = column[ProcessId]("scenario_id")

    override def * : ProvenShape[DeploymentEntityData] =
      (id, scenarioId) <> (DeploymentEntityData.apply _ tupled, DeploymentEntityData.unapply)

    private def scenarios_fk: ForeignKeyQuery[ProcessEntityFactory#ProcessEntity, ProcessEntityData] =
      foreignKey("deployments_scenarios_fk", scenarioId, processesTable)(
        _.id,
        onUpdate = ForeignKeyAction.Cascade,
        onDelete = ForeignKeyAction.NoAction
      )

  }

  protected implicit def deploymentIdMapping: BaseColumnType[DeploymentIdNG] =
    MappedColumnType.base[DeploymentIdNG, UUID](_.value, DeploymentIdNG.apply)

}

object DeploymentEntityFactory {

  final case class DeploymentEntityData(id: DeploymentIdNG, scenarioId: ProcessId)

}
