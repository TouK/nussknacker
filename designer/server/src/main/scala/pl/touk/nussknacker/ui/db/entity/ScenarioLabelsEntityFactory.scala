package pl.touk.nussknacker.ui.db.entity

import pl.touk.nussknacker.engine.api.process.ProcessId
import slick.lifted.{TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.NotNull

trait ScenarioLabelsEntityFactory extends BaseEntityFactory {

  import profile.api._

  val processesTable: LTableQuery[ProcessEntityFactory#ProcessEntity]

  class ScenarioLabelsEntity(tag: Tag) extends Table[ScenarioLabelEntityData](tag, "scenario_labels") {

    def label = column[String]("label")

    def scenarioId = column[ProcessId]("scenario_id", NotNull)

    def * = (label, scenarioId) <> (ScenarioLabelEntityData.apply _ tupled, ScenarioLabelEntityData.unapply)

    def pk = primaryKey("pk_scenario_label", (label, scenarioId))

    def scenario = foreignKey("label_scenario_fk", scenarioId, processesTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )

  }

  val labelsTable: LTableQuery[ScenarioLabelsEntityFactory#ScenarioLabelsEntity] = LTableQuery(
    new ScenarioLabelsEntity(_)
  )

}

final case class ScenarioLabelEntityData(name: String, scenarioId: ProcessId)
