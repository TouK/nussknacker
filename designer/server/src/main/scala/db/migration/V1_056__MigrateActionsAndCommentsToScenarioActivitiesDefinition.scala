package db.migration

import com.typesafe.scalalogging.LazyLogging
import db.migration.V1_055__CreateScenarioActivitiesDefinition.ScenarioActivitiesDefinitions
import db.migration.V1_056__MigrateActionsAndCommentsToScenarioActivitiesDefinition.Migration
import pl.touk.nussknacker.ui.db.entity.{ScenarioActivityEntityFactory, ScenarioActivityType}
import pl.touk.nussknacker.ui.db.migration.SlickMigration
import slick.jdbc.JdbcProfile
import slick.lifted.{ProvenShape, TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.NotNull

import java.sql.Timestamp
import java.util.UUID

trait V1_056__MigrateActionsAndCommentsToScenarioActivitiesDefinition extends SlickMigration with LazyLogging {

  import profile.api._

  override def migrateActions: DBIOAction[Any, NoStream, Effect.All] = {
    new Migration(profile).migrateActions
  }

}

object V1_056__MigrateActionsAndCommentsToScenarioActivitiesDefinition extends LazyLogging {

  class Migration(val profile: JdbcProfile) extends ScenarioActivityEntityFactory {

    import profile.api._

    private val scenarioActivitiesDefinitions = new ScenarioActivitiesDefinitions(profile)
    private val processActionsDefinitions     = new ProcessActionsDefinitions(profile)
    private val commentsDefinitions           = new CommentsDefinitions(profile)

    def migrateActions: DBIOAction[Int, NoStream, Effect.All] = {

      val insertQuery =
        processActionsDefinitions.table
          .joinLeft(commentsDefinitions.table)
          .on(_.commentId === _.id)
          .map { case (processAction, maybeComment) =>
            (
              activityType(processAction.actionName), // activityType - converted from action name
              processAction.processId,                // scenarioId
              processAction.id,                       // activityId
              None: Option[String],                   // userId - always absent in old actions
              processAction.user,                     // userName
              processAction.impersonatedByIdentity,   // impersonatedByUserId
              processAction.impersonatedByUsername,   // impersonatedByUserName
              processAction.user.?,                   // lastModifiedByUserName
              processAction.createdAt.?,              // lastModifiedAt
              processAction.createdAt,                // createdAt
              processAction.processVersionId,         // scenarioVersion
              maybeComment.map(_.content),            // comment
              None: Option[Long],                     // attachmentId
              processAction.performedAt,              // finishedAt
              processAction.state.?,                  // state
              processAction.failureMessage,           // errorMessage
              None: Option[String],                   // buildInfo - always absent in old actions
              "{}"                                    // additionalProperties - always empty in old actions
            )
          }

      // Slick generates single "insert from select" query and operation is performed solely on db
      scenarioActivitiesDefinitions.scenarioActivitiesTable.map(_.tupleWithoutAutoIncId).forceInsertQuery(insertQuery)
    }

    def activityType(actionNameRep: Rep[String]): Rep[String] = {
      val customActionPrefix = s"CUSTOM_ACTION_["
      val customActionSuffix = "]"
      Case
        .If(actionNameRep === "DEPLOY")
        .Then(ScenarioActivityType.ScenarioDeployed.entryName)
        .If(actionNameRep === "CANCEL")
        .Then(ScenarioActivityType.ScenarioCanceled.entryName)
        .If(actionNameRep === "ARCHIVE")
        .Then(ScenarioActivityType.ScenarioArchived.entryName)
        .If(actionNameRep === "UNARCHIVE")
        .Then(ScenarioActivityType.ScenarioUnarchived.entryName)
        .If(actionNameRep === "PAUSE")
        .Then(ScenarioActivityType.ScenarioPaused.entryName)
        .If(actionNameRep === "RENAME")
        .Then(ScenarioActivityType.ScenarioNameChanged.entryName)
        .If(actionNameRep === "run now")
        .Then(ScenarioActivityType.PerformedSingleExecution.entryName)
        .Else(actionNameRep.reverseString.++(customActionPrefix.reverse).reverseString.++(customActionSuffix))
    }

  }

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
