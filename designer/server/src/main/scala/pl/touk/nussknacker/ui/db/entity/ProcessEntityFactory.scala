package pl.touk.nussknacker.ui.db.entity

import pl.touk.nussknacker.engine.api.deployment.ProcessActionId
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, ProcessingType, VersionId}
import slick.lifted.{ProvenShape, TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.NotNull

import java.sql.Timestamp

trait ProcessEntityFactory extends BaseEntityFactory {

  import profile.api._

  val processesTable: LTableQuery[ProcessEntityFactory#ProcessEntity] = LTableQuery(new ProcessEntity(_))

  class ProcessEntity(tag: Tag) extends Table[ProcessEntityData](tag, "processes") {

    def id: Rep[ProcessId] = column[ProcessId]("id", O.PrimaryKey, O.AutoInc)

    def name: Rep[ProcessName] = column[ProcessName]("name", NotNull)

    def description: Rep[Option[String]] = column[Option[String]]("description", O.Length(1000))

    def processCategory: Rep[String] = column[String]("category", NotNull)

    def processingType: Rep[ProcessingType] = column[ProcessingType]("processing_type", NotNull)

    def isFragment: Rep[Boolean] = column[Boolean]("is_fragment", NotNull)

    def isArchived: Rep[Boolean] = column[Boolean]("is_archived", NotNull)

    def createdAt: Rep[Timestamp] = column[Timestamp]("created_at", NotNull)

    def createdBy: Rep[String] = column[String]("created_by", NotNull)

    def latestVersionId: Rep[Option[VersionId]] = column[Option[VersionId]]("latest_version_id")

    def latestFinishedActionId: Rep[Option[ProcessActionId]] =
      column[Option[ProcessActionId]]("latest_finished_action_id")

    def latestFinishedCancelActionId: Rep[Option[ProcessActionId]] =
      column[Option[ProcessActionId]]("latest_finished_cancel_action_id")

    def latestFinishedDeployActionId: Rep[Option[ProcessActionId]] =
      column[Option[ProcessActionId]]("latest_finished_deploy_action_id")

    def * : ProvenShape[ProcessEntityData] =
      (
        id,
        name,
        description,
        processCategory,
        processingType,
        isFragment,
        isArchived,
        createdAt,
        createdBy,
        latestVersionId,
        latestFinishedActionId,
        latestFinishedCancelActionId,
        latestFinishedDeployActionId
      ) <> (
        ProcessEntityData.apply _ tupled, ProcessEntityData.unapply
      )

  }

}

// TODO: Rename to ScenarioMetadata
final case class ProcessEntityData(
    id: ProcessId,
    name: ProcessName,
    description: Option[String],
    processCategory: String,
    processingType: ProcessingType,
    isFragment: Boolean,
    isArchived: Boolean,
    createdAt: Timestamp,
    createdBy: String,
    latestVersionId: Option[VersionId], // None only when a new process is inserted, but it's initial version isn't yet
    // in practice the inserts happen in one transaction, and could be handled with a DEFERRED foreign key, but e.g. HSQL doesn't support it
    latestFinishedActionId: Option[ProcessActionId],
    latestFinishedCancelActionId: Option[ProcessActionId],
    latestFinishedDeployActionId: Option[ProcessActionId]
)
