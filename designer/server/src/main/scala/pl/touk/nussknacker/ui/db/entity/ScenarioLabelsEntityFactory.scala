package pl.touk.nussknacker.ui.db.entity

import pl.touk.nussknacker.engine.api.process.ProcessId
import slick.lifted.{TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.NotNull

trait ScenarioLabelsEntityFactory extends BaseEntityFactory {

  import profile.api._

  val processesTable: LTableQuery[ProcessEntityFactory#ProcessEntity]

  class ScenarioLabelsEntity(tag: Tag) extends Table[ScenarioLabelEntityData](tag, "tags") {

    def name = column[String]("name")

    def scenarioId = column[ProcessId]("process_id", NotNull)

    def * = (name, scenarioId) <> (ScenarioLabelEntityData.apply _ tupled, ScenarioLabelEntityData.unapply)

    def pk = primaryKey("pk_tag", (name, scenarioId))

    def scenario = foreignKey("tag-process-fk", scenarioId, processesTable)(
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
