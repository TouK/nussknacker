package pl.touk.nussknacker.ui.db.entity

import pl.touk.nussknacker.engine.api.deployment.ProcessActionState.ProcessActionState
import pl.touk.nussknacker.engine.api.deployment.{ProcessActionId, ScenarioActionName}
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import slick.lifted.{ForeignKeyQuery, ProvenShape, TableQuery => LTableQuery}

import java.sql.Timestamp
import java.time.Instant

trait ProcessActionEntityFactory extends BaseEntityFactory {

  import profile.api._

  val processActionsTable: LTableQuery[ProcessActionEntityFactory#ProcessActionEntity] =
    LTableQuery(new ProcessActionEntity(_))

  val processVersionsTable: LTableQuery[ProcessVersionEntityFactory#ProcessVersionEntity]
  val commentsTable: LTableQuery[CommentEntityFactory#CommentEntity]

  class ProcessActionEntity(tag: Tag) extends Table[ProcessActionEntityData](tag, "process_actions") {
    def id: Rep[ProcessActionId] = column[ProcessActionId]("id", O.PrimaryKey)

    def processId: Rep[ProcessId] = column[ProcessId]("process_id")

    def processVersionId: Rep[Option[VersionId]] = column[Option[VersionId]]("process_version_id")

    def createdAt: Rep[Timestamp] = column[Timestamp]("created_at")

    def performedAt: Rep[Option[Timestamp]] = column[Option[Timestamp]]("performed_at")

    def user: Rep[String] = column[String]("user")

    def buildInfo: Rep[Option[String]] = column[Option[String]]("build_info")

    def actionName: Rep[ScenarioActionName] = column[ScenarioActionName]("action_name")

    def state: Rep[ProcessActionState] = column[ProcessActionState]("state")

    def failureMessage: Rep[Option[String]] = column[Option[String]]("failure_message")

    def commentId: Rep[Option[Long]] = column[Option[Long]]("comment_id")

    def processes_fk: ForeignKeyQuery[ProcessVersionEntityFactory#ProcessVersionEntity, ProcessVersionEntityData] =
      foreignKey("process_actions_version_fk", (processId, processVersionId), processVersionsTable)(
        procV => (procV.processId, procV.id ?),
        onUpdate = ForeignKeyAction.Cascade,
        onDelete = ForeignKeyAction.NoAction
      )

    def comment_fk: ForeignKeyQuery[CommentEntityFactory#CommentEntity, CommentEntityData] =
      foreignKey("process_actions_comment_fk", commentId, commentsTable)(
        _.id.?,
        onUpdate = ForeignKeyAction.Cascade,
        onDelete = ForeignKeyAction.SetNull
      )

    def * : ProvenShape[ProcessActionEntityData] = (
      id,
      processId,
      processVersionId,
      user,
      createdAt,
      performedAt,
      actionName,
      state,
      failureMessage,
      commentId,
      buildInfo
    ) <> (
      ProcessActionEntityData.apply _ tupled, ProcessActionEntityData.unapply
    )

  }

}

final case class ProcessActionEntityData(
    id: ProcessActionId,
    processId: ProcessId,
    processVersionId: Option[VersionId],
    user: String,
    createdAt: Timestamp,
    performedAt: Option[Timestamp],
    actionName: ScenarioActionName,
    state: ProcessActionState,
    failureMessage: Option[String],
    commentId: Option[Long],
    buildInfo: Option[String]
) {

  lazy val createdAtTime: Instant           = createdAt.toInstant
  lazy val performedAtTime: Option[Instant] = performedAt.map(_.toInstant)
  lazy val isDeployed: Boolean              = actionName == ScenarioActionName.Deploy
}
