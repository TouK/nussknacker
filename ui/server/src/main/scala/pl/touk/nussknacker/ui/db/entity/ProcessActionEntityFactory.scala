package pl.touk.nussknacker.ui.db.entity

import java.sql.Timestamp
import java.time.LocalDateTime
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.ui.db.DateUtils
import slick.ast.BaseTypedType
import slick.jdbc.{JdbcProfile, JdbcType}
import slick.lifted.{ForeignKeyQuery, ProvenShape, TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.{NotNull, Nullable}

trait ProcessActionEntityFactory {

  protected val profile: JdbcProfile
  import profile.api._

  implicit def deploymentMapper: JdbcType[ProcessActionType] with BaseTypedType[ProcessActionType] =
    MappedColumnType.base[ProcessActionType, String](_.toString, ProcessActionType.withName)

  val processActionsTable: LTableQuery[ProcessActionEntityFactory#ProcessActionEntity] =
    LTableQuery(new ProcessActionEntity(_))

  val processVersionsTable: LTableQuery[ProcessVersionEntityFactory#ProcessVersionEntity]
  val commentsTable: LTableQuery[CommentEntityFactory#CommentEntity]

  class ProcessActionEntity(tag: Tag) extends Table[ProcessActionEntityData](tag, "process_actions") {
    def processId: Rep[Long] = column[Long]("process_id", NotNull)

    def processVersionId: Rep[Long] = column[Long]("process_version_id", Nullable)

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
      (ProcessActionEntityData.create _).tupled,
      (e: ProcessActionEntityData) => ProcessActionEntityData.unapply(e).map { t => (t._1.value, t._2.value, t._3, t._4, t._5, t._6, t._7) }
    )
  }
}

object ProcessActionEntityData {
  def create(processId: Long, processVersionId: Long, user: String, performedAt: Timestamp, action: ProcessActionType, commentId: Option[Long], buildInfo: Option[String]): ProcessActionEntityData =
    ProcessActionEntityData(processId, VersionId(processVersionId), user, performedAt, action, commentId, buildInfo)
}

case class ProcessActionEntityData(processId: Long,
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
