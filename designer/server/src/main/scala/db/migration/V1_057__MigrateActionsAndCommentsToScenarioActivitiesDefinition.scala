package db.migration

import com.typesafe.scalalogging.LazyLogging
import db.migration.V1_056__CreateScenarioActivitiesDefinition.ScenarioActivitiesDefinitions
import db.migration.V1_057__MigrateActionsAndCommentsToScenarioActivitiesDefinition.Migration
import pl.touk.nussknacker.ui.db.entity.{ScenarioActivityEntityFactory, ScenarioActivityType}
import pl.touk.nussknacker.ui.db.migration.SlickMigration
import slick.ast.Library.JdbcFunction
import slick.jdbc.JdbcProfile
import slick.lifted.{ProvenShape, TableQuery => LTableQuery}
import slick.lifted.FunctionSymbolExtensionMethods.functionSymbolExtensionMethods
import slick.sql.SqlProfile.ColumnOption.NotNull

import java.sql.Timestamp
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

trait V1_057__MigrateActionsAndCommentsToScenarioActivitiesDefinition extends SlickMigration with LazyLogging {

  import profile.api._

  override def migrateActions: DBIOAction[Any, NoStream, Effect.All] =
    createGenerateRandomUuidFunction().flatMap[Any, NoStream, Effect.All](_ => new Migration(profile).migrate)

  protected def createGenerateRandomUuidFunction(): DBIOAction[Any, NoStream, Effect.All]

}

object V1_057__MigrateActionsAndCommentsToScenarioActivitiesDefinition extends LazyLogging {

  class Migration(val profile: JdbcProfile) extends ScenarioActivityEntityFactory {

    import profile.api._

    private val scenarioActivitiesDefinitions = new ScenarioActivitiesDefinitions(profile)
    private val processActionsDefinitions     = new ProcessActionsDefinitions(profile)
    private val commentsDefinitions           = new CommentsDefinitions(profile)

    // Migrate old actions, with their corresponding comments

    def migrate: DBIOAction[Unit, NoStream, Effect.All] = for {
      _ <- migrateActions
      _ <- migrateComments
    } yield ()

    private def migrateActions: DBIOAction[Int, NoStream, Effect.All] = {
      val insertQuery =
        processActionsDefinitions.table
          .joinLeft(commentsDefinitions.table)
          .on(_.commentId === _.id)
          .map { case (processAction, maybeComment) =>
            (
              activityType(processAction.actionName),               // activityType - converted from action name
              processAction.processId,                              // scenarioId
              processAction.id,                                     // activityId
              None: Option[String],                                 // userId - always absent in old actions
              processAction.user,                                   // userName
              processAction.impersonatedByIdentity,                 // impersonatedByUserId
              processAction.impersonatedByUsername,                 // impersonatedByUserName
              processAction.user.?,                                 // lastModifiedByUserName
              processAction.createdAt.?,                            // lastModifiedAt
              processAction.createdAt,                              // createdAt
              processAction.processVersionId,                       // scenarioVersion
              maybeComment.map(_.content).map(removeCommentPrefix), // comment
              None: Option[Long],                                   // attachmentId
              processAction.performedAt,                            // finishedAt
              processAction.state.?,                                // state
              processAction.failureMessage,                         // errorMessage
              processAction.buildInfo,                              // buildInfo
              "{}"                                                  // additionalProperties always empty in old actions
            )
          }

      // Slick generates single "insert from select" query and operation is performed solely on db
      scenarioActivitiesDefinitions.scenarioActivitiesTable.map(_.tupleWithoutAutoIncId).forceInsertQuery(insertQuery)
    }

    // Migrate old comments, that were standalone, not assigned to actions
    def migrateComments: DBIOAction[Int, NoStream, Effect.All] = {
      val insertQuery =
        commentsDefinitions.table
          .joinLeft(processActionsDefinitions.table)
          .on(_.id === _.commentId)
          .filter { case (_, action) => action.isEmpty }
          .map(_._1)
          .map { comment =>
            (
              ScenarioActivityType.CommentAdded.entryName,             // activityType - converted from action name
              comment.processId,                                       // scenarioId
              new JdbcFunction("generate_random_uuid").column[UUID](), // activityId
              None: Option[String],                                    // userId - always absent in old actions
              comment.user,                                            // userName
              comment.impersonatedByIdentity,                          // impersonatedByUserId
              comment.impersonatedByUsername,                          // impersonatedByUserName
              comment.user.?,                                          // lastModifiedByUserName
              comment.createDate.?,                                    // lastModifiedAt
              comment.createDate,                                      // createdAt
              comment.processVersionId.?,                              // scenarioVersion
              comment.content.?,                                       // comment
              None: Option[Long],                                      // attachmentId
              comment.createDate.?,                                    // finishedAt
              None: Option[String],                                    // state
              None: Option[String],                                    // errorMessage
              None: Option[String],                                    // modelInfo - always absent in old actions
              "{}" // additionalProperties always empty in old actions
            )
          }

      // Slick generates single "insert from select" query and operation is performed solely on db
      scenarioActivitiesDefinitions.scenarioActivitiesTable.map(_.tupleWithoutAutoIncId).forceInsertQuery(insertQuery)
    }

    private def activityType(actionNameRep: Rep[String]): Rep[String] = {
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

    private def removeCommentPrefix(commentRep: Rep[String]): Rep[String] = {
      val prefixDeploymentComment = "Deployment: "
      val prefixCanceledComment   = "Stop: "
      val prefixRunNowComment     = "Run now: "
      Case
        .If(commentRep.startsWith(prefixDeploymentComment))
        .Then(commentRep.substring(LiteralColumn(prefixDeploymentComment.length)))
        .If(commentRep.startsWith(prefixCanceledComment))
        .Then(commentRep.substring(LiteralColumn(prefixCanceledComment.length)))
        .If(commentRep.startsWith(prefixRunNowComment))
        .Then(commentRep.substring(LiteralColumn(prefixRunNowComment.length)))
        .Else(commentRep)
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
        ProcessActionEntityData.apply _ tupled,
        ProcessActionEntityData.unapply
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
          CommentEntityData.apply _ tupled,
          CommentEntityData.unapply
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
