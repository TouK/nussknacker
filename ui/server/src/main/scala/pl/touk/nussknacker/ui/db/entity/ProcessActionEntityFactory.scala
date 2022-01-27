package pl.touk.nussknacker.ui.db.entity

import java.sql.Timestamp
import java.time.LocalDateTime
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.ui.db.DateUtils
import slick.lifted.{ForeignKeyQuery, ProvenShape, TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.{NotNull, Nullable}

trait ProcessActionEntityFactory extends BaseEntityFactory {

  import profile.api._

  val processActionsTable: LTableQuery[ProcessActionEntityFactory#ProcessActionEntity] =
    LTableQuery(new ProcessActionEntity(_))

  val processVersionsTable: LTableQuery[ProcessVersionEntityFactory#ProcessVersionEntity]
  val commentsTable: LTableQuery[CommentEntityFactory#CommentEntity]

  class ProcessActionEntity(tag: Tag) extends Table[ProcessActionEntityData](tag, "process_actions") {
    def processId: Rep[ProcessId] = column[ProcessId]("process_id", NotNull)

    def processVersionId: Rep[VersionId] = column[VersionId]("process_version_id", Nullable)

    def performedAt: Rep[Timestamp] = column[Timestamp]("performed_at", NotNull)

    def user: Rep[String] = column[String]("user", NotNull)

    def buildInfo: Rep[Option[String]] = column[Option[String]]("build_info", Nullable)

    def action: Rep[ProcessActionType] = column[ProcessActionType]("action", NotNull)

    def commentId: Rep[Option[Long]] = column[Option[Long]]("comment_id", Nullable)

    def pk = primaryKey("process_actions_pk", (processId, processVersionId, performedAt))

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

    def * : ProvenShape[ProcessActionEntityData] = (processId, processVersionId, user, performedAt, action, commentId, buildInfo) <> (
      ProcessActionEntityData.apply _ tupled, ProcessActionEntityData.unapply
    )
  }
}

case class ProcessActionEntityData(processId: ProcessId,
                                   processVersionId: VersionId,
                                   user: String,
                                   performedAt: Timestamp,
                                   action: ProcessActionType,
                                   commentId: Option[Long],
                                   buildInfo: Option[String]) {

  lazy val performedAtTime: LocalDateTime = DateUtils.toLocalDateTime(performedAt)
  lazy val isDeployed: Boolean = action.equals(ProcessActionType.Deploy)
  lazy val isCanceled: Boolean = action.equals(ProcessActionType.Cancel)
}
