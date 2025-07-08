package pl.touk.nussknacker.ui.db.entity

import pl.touk.nussknacker.engine.api.deployment.ScenarioActivityId
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

    def deploymentId: Rep[ScenarioActivityId] = column[ScenarioActivityId]("deployment_id")

    def collectorId: Rep[String] = column[String]("collector_id")

    def liveData: Rep[Option[String]] = column[Option[String]]("live_data")

    def updatedAt: Rep[Long] = column[Long]("updated_at", NotNull)

    override def * =
      (
        scenarioId,
        deploymentId,
        collectorId,
        liveData,
        updatedAt,
      ) <> ((LiveDataEntityData.apply _).tupled, LiveDataEntityData.unapply)

  }

}

final case class LiveDataEntityData(
    scenarioId: ProcessId,
    deploymentId: ScenarioActivityId,
    collectorId: String,
    liveData: Option[String],
    updatedAt: Long,
)
