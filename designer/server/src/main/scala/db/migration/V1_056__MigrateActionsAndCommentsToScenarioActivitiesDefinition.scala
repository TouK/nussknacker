package db.migration

import db.migration.V1_055__CreateScenarioActivitiesDefinition.{
  ScenarioActivitiesDefinitions,
  ScenarioActivityEntityData
}
import db.migration.V1_056__MigrateActionsAndCommentsToScenarioActivities.{
  CommentsDefinitions,
  ProcessActionsDefinitions
}
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.management.periodic.InstantBatchCustomAction
import pl.touk.nussknacker.ui.db.entity.ScenarioActivityType
import pl.touk.nussknacker.ui.db.migration.SlickMigration
import slick.jdbc.JdbcProfile
import slick.lifted.{ProvenShape, TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.NotNull

import java.sql.Timestamp
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

trait V1_056__MigrateActionsAndCommentsToScenarioActivitiesDefinition extends SlickMigration {

  import profile.api._

  private val scenarioActivitiesDefinitions = new ScenarioActivitiesDefinitions(profile)
  private val processActionsDefinitions     = new ProcessActionsDefinitions(profile)
  private val commentsDefinitions           = new CommentsDefinitions(profile)

  override def migrateActions: DBIOAction[Any, NoStream, _ <: Effect] = {
    processActionsDefinitions.table
      .joinLeft(commentsDefinitions.table)
      .on(_.commentId === _.id)
      .result
      .map { actionsWithComments =>
        actionsWithComments.map { case (processAction, maybeComment) =>
          ScenarioActivityEntityData(
            id = -1L,
            activityType = activityTypeStr(processAction.actionName),
            scenarioId = processAction.processId,
            activityId = processAction.id,
            userId = processAction.user, // todo
            userName = processAction.user,
            impersonatedByUserId = processAction.impersonatedByIdentity,
            impersonatedByUserName = processAction.impersonatedByUsername,
            lastModifiedByUserName = processAction.impersonatedByUsername,
            createdAt = processAction.createdAt,
            scenarioVersion = processAction.processVersionId,
            comment = maybeComment.map(_.content),
            attachmentId = None,
            finishedAt = processAction.performedAt,
            state = Some(processAction.state),
            errorMessage = processAction.failureMessage,
            buildInfo = None,
            additionalProperties = Map.empty[String, String].asJson.noSpaces
          )
        }.toList
      }
      .flatMap(scenarioActivitiesDefinitions.scenarioActivitiesTable ++= _)
  }

  private def activityTypeStr(actionName: String) = {
    val activityType = ScenarioActionName(actionName) match {
      case ScenarioActionName.Deploy =>
        ScenarioActivityType.ScenarioDeployed
      case ScenarioActionName.Cancel =>
        ScenarioActivityType.ScenarioCanceled
      case ScenarioActionName.Archive =>
        ScenarioActivityType.ScenarioArchived
      case ScenarioActionName.UnArchive =>
        ScenarioActivityType.ScenarioUnarchived
      case ScenarioActionName.Pause =>
        ScenarioActivityType.ScenarioPaused
      case ScenarioActionName.Rename =>
        ScenarioActivityType.ScenarioNameChanged
      case InstantBatchCustomAction.name =>
        ScenarioActivityType.PerformedSingleExecution
      case otherCustomName =>
        ScenarioActivityType.CustomAction(otherCustomName.value)
    }
    activityType.entryName
  }

}

object V1_056__MigrateActionsAndCommentsToScenarioActivitiesDefinition {

  class ProcessActionsDefinitions(val profile: JdbcProfile) {
    import profile.api._

    val table: LTableQuery[ProcessActionEntity] = LTableQuery(new ProcessActionEntity(_))

    class ProcessActionEntity(tag: Tag) extends Table[ProcessActionEntityData](tag, "process_actions") {
      def id: Rep[UUID] = column[UUID]("id", O.PrimaryKey)

      def processId: Rep[Long] = column[Long]("process_id")

      def processVersionId: Rep[Option[Long]] = column[Option[Long]]("process_version_id")

      def createdAt: Rep[Timestamp] = column[Timestamp]("created_at")

      def performedAt: Rep[Option[Timestamp]] = column[Option[Timestamp]]("performed_at")

      def user: Rep[String] = column[String]("user")

      def impersonatedByIdentity = column[Option[String]]("impersonated_by_identity")

      def impersonatedByUsername = column[Option[String]]("impersonated_by_username")

      def buildInfo: Rep[Option[String]] = column[Option[String]]("build_info")

      def actionName: Rep[String] = column[String]("action_name")

      def state: Rep[String] = column[String]("state")

      def failureMessage: Rep[Option[String]] = column[Option[String]]("failure_message")

      def commentId: Rep[Option[Long]] = column[Option[Long]]("comment_id")

      def * : ProvenShape[ProcessActionEntityData] = (
        id,
        processId,
        processVersionId,
        user,
        impersonatedByIdentity,
        impersonatedByUsername,
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

  sealed case class ProcessActionEntityData(
      id: UUID,
      processId: Long,
      processVersionId: Option[Long],
      user: String,
      impersonatedByIdentity: Option[String],
      impersonatedByUsername: Option[String],
      createdAt: Timestamp,
      performedAt: Option[Timestamp],
      actionName: String,
      state: String,
      failureMessage: Option[String],
      commentId: Option[Long],
      buildInfo: Option[String]
  )

  class CommentsDefinitions(val profile: JdbcProfile) {
    import profile.api._
    val table: LTableQuery[CommentEntity] = LTableQuery(new CommentEntity(_))

    class CommentEntity(tag: Tag) extends Table[CommentEntityData](tag, "process_comments") {

      def id: Rep[Long] = column[Long]("id", O.PrimaryKey)

      def processId: Rep[Long] = column[Long]("process_id", NotNull)

      def processVersionId: Rep[Long] = column[Long]("process_version_id", NotNull)

      def content: Rep[String] = column[String]("content", NotNull)

      def createDate: Rep[Timestamp] = column[Timestamp]("create_date", NotNull)

      def user: Rep[String] = column[String]("user", NotNull)

      def impersonatedByIdentity = column[Option[String]]("impersonated_by_identity")

      def impersonatedByUsername = column[Option[String]]("impersonated_by_username")

      override def * =
        (
          id,
          processId,
          processVersionId,
          content,
          user,
          impersonatedByIdentity,
          impersonatedByUsername,
          createDate
        ) <> (
          CommentEntityData.apply _ tupled, CommentEntityData.unapply
        )

    }

  }

  final case class CommentEntityData(
      id: Long,
      processId: Long,
      processVersionId: Long,
      content: String,
      user: String,
      impersonatedByIdentity: Option[String],
      impersonatedByUsername: Option[String],
      createDate: Timestamp,
  )

}
