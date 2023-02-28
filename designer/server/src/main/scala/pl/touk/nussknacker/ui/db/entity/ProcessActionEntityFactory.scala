package pl.touk.nussknacker.ui.db.entity


import java.sql.Timestamp
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.ProcessActionState
import pl.touk.nussknacker.engine.api.deployment.ProcessActionState.ProcessActionState
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import slick.lifted.{ForeignKeyQuery, ProvenShape, TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.{NotNull, Nullable}

import java.time.Instant

trait ProcessActionEntityFactory extends BaseEntityFactory {

  import profile.api._

  val processActionsTable: LTableQuery[ProcessActionEntityFactory#ProcessActionEntity] =
    LTableQuery(new ProcessActionEntity(_))

  val processVersionsTable: LTableQuery[ProcessVersionEntityFactory#ProcessVersionEntity]
  val commentsTable: LTableQuery[CommentEntityFactory#CommentEntity]

  class ProcessActionEntity(tag: Tag) extends Table[ProcessActionEntityData](tag, "process_actions") {
    def id: Rep[ProcessActionId] = column[ProcessActionId]("id", O.PrimaryKey, O.AutoInc)

    def processId: Rep[ProcessId] = column[ProcessId]("process_id", NotNull)

    def processVersionId: Rep[VersionId] = column[VersionId]("process_version_id", Nullable)

    def createdAt: Rep[Timestamp] = column[Timestamp]("created_at", NotNull)

    def performedAt: Rep[Timestamp] = column[Timestamp]("performed_at", Nullable)

    def user: Rep[String] = column[String]("user", NotNull)

    def buildInfo: Rep[Option[String]] = column[Option[String]]("build_info", Nullable)

    def action: Rep[ProcessActionType] = column[ProcessActionType]("action", NotNull)

    def state: Rep[ProcessActionState] = column[ProcessActionState]("state", NotNull)

    def commentId: Rep[Option[Long]] = column[Option[Long]]("comment_id", Nullable)

    def processes_fk: ForeignKeyQuery[ProcessVersionEntityFactory#ProcessVersionEntity, ProcessVersionEntityData] = foreignKey("process_actions_version_fk", (processId, processVersionId), processVersionsTable)(
      procV => (procV.processId, procV.id),
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.NoAction
    )

    def comment_fk: ForeignKeyQuery[CommentEntityFactory#CommentEntity, CommentEntityData] = foreignKey("process_actions_comment_fk", commentId, commentsTable)(
      _.id.?,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.SetNull
    )

    def * : ProvenShape[ProcessActionEntityData] = (id, processId, processVersionId, user, createdAt, performedAt, action, state, commentId, buildInfo) <> (
      ProcessActionEntityData.apply _ tupled, ProcessActionEntityData.unapply
    )
  }
}

case class ProcessActionEntityData(id: ProcessActionId,
                                   processId: ProcessId,
                                   processVersionId: VersionId,
                                   user: String,
                                   createdAt: Timestamp,
                                   performedAt: Timestamp,
                                   action: ProcessActionType,
                                   state: ProcessActionState,
                                   commentId: Option[Long],
                                   buildInfo: Option[String]) {

  lazy val performedAtTime: Instant = performedAt.toInstant
  lazy val isDeployed: Boolean = action.equals(ProcessActionType.Deploy)
  lazy val isInProgress: Boolean = state.equals(ProcessActionState.InProgress)
  lazy val isFinished: Boolean = state.equals(ProcessActionState.Finished)
}

final case class ProcessActionId(value: Long)