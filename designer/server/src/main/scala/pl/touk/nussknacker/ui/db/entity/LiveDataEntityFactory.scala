package pl.touk.nussknacker.ui.db.entity

import pl.touk.nussknacker.engine.api.process.ProcessId
import slick.lifted.{TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.NotNull

trait LiveDataEntityFactory extends BaseEntityFactory {

  import profile.apiWithEnforcedSchema._

  val flinkLiveDataTable: LTableQuery[LiveDataEntityFactory#LiveDataEntity] = LTableQuery(
    new LiveDataEntity(_)
  )

  class LiveDataEntity(tag: Tag) extends TableWithSchema[LiveDataEntityData](tag, "live_data") {

    def scenarioId: Rep[ProcessId] = column[ProcessId]("scenario_id")

    def deploymentId: Rep[String] = column[String]("deployment_id")

    def externalDeploymentId: Rep[String] = column[String]("external_deployment_id")

    def collectorId: Rep[String] = column[String]("collector_id")

    def liveData: Rep[Option[String]] = column[Option[String]]("live_data")

    def updatedAt: Rep[Long] = column[Long]("updated_at", NotNull)

    def pk = primaryKey("pk_scenario_activity_collector_ids", (scenarioId, deploymentId, collectorId))

    override def * =
      (
        scenarioId,
        deploymentId,
        externalDeploymentId,
        collectorId,
        liveData,
        updatedAt,
      ) <> ((LiveDataEntityData.apply _).tupled, LiveDataEntityData.unapply)

  }

}

final case class LiveDataEntityData(
    scenarioId: ProcessId,
    deploymentId: String,
    externalDeploymentId: String,
    collectorId: String,
    liveData: Option[String],
    updatedAt: Long,
)
