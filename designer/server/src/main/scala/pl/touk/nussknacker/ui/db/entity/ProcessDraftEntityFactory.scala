package pl.touk.nussknacker.ui.db.entity

import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import slick.lifted.{ForeignKeyQuery, ProvenShape, TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.NotNull

import java.sql.Timestamp

trait ProcessDraftEntityFactory extends BaseEntityFactory {

  import profile.apiWithEnforcedSchema._

  val processesTable: LTableQuery[ProcessEntityFactory#ProcessEntity]

  class ProcessDraftEntity(tag: Tag) extends TableWithSchema[ProcessDraftEntityData](tag, "process_drafts") {

    def processId: Rep[ProcessId] = column[ProcessId]("process_id", NotNull)

    def scenarioGraph: Rep[String] = column[String]("scenario_graph", NotNull)

    def baseVersionId: Rep[Option[VersionId]] = column[Option[VersionId]]("base_version_id")

    def updatedAt: Rep[Timestamp] = column[Timestamp]("updated_at", NotNull)

    def updatedBy: Rep[String] = column[String]("updated_by", NotNull)

    def pk = primaryKey("process_drafts_pk", processId)

    private def process: ForeignKeyQuery[ProcessEntityFactory#ProcessEntity, ProcessEntityData] =
      foreignKey("process_drafts_process_fk", processId, processesTable)(
        _.id,
        onUpdate = ForeignKeyAction.Cascade,
        onDelete = ForeignKeyAction.Cascade
      )

    override def * : ProvenShape[ProcessDraftEntityData] =
      (processId, scenarioGraph, baseVersionId, updatedAt, updatedBy) <> (
        (ProcessDraftEntityData.apply _).tupled,
        ProcessDraftEntityData.unapply
      )

  }

  val processDraftsTable: LTableQuery[ProcessDraftEntityFactory#ProcessDraftEntity] =
    LTableQuery(new ProcessDraftEntity(_))

}

final case class ProcessDraftEntityData(
    processId: ProcessId,
    scenarioGraph: String,
    baseVersionId: Option[VersionId],
    updatedAt: Timestamp,
    updatedBy: String,
)
