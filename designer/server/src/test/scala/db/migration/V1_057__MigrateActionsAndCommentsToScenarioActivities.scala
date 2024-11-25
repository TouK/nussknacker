package db.migration

import db.migration.V1_056__CreateScenarioActivitiesDefinition.{
  ScenarioActivitiesDefinitions,
  ScenarioActivityEntityData
}
import db.migration.V1_057__MigrateActionsAndCommentsToScenarioActivitiesDefinition._
import io.circe.syntax.EncoderOps
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.api.{MetaData, ProcessAdditionalFields, RequestResponseMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.common.periodic.InstantBatchCustomAction
import pl.touk.nussknacker.restmodel.component.ScenarioComponentsUsages
import pl.touk.nussknacker.test.base.db.WithHsqlDbTesting
import pl.touk.nussknacker.test.base.it.NuItTest
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig
import pl.touk.nussknacker.test.utils.domain.TestFactory.newDBIOActionRunner
import pl.touk.nussknacker.ui.db.NuTables
import pl.touk.nussknacker.ui.db.entity.{AdditionalProperties, ProcessEntityData, ProcessVersionEntityData}
import pl.touk.nussknacker.ui.process.repository.activities.DbScenarioActivityRepository
import slick.jdbc.{HsqldbProfile, JdbcProfile}

import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class V1_057__MigrateActionsAndCommentsToScenarioActivities
    extends AnyFreeSpecLike
    with Matchers
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithHsqlDbTesting
    with NuTables {

  override protected val profile: JdbcProfile = HsqldbProfile

  import profile.api._

  private val runner = newDBIOActionRunner(testDbRef)

  private val migration                 = new Migration(HsqldbProfile)
  private val processActionsDefinitions = new ProcessActionsDefinitions(profile)
  private val commentsDefinitions       = new CommentsDefinitions(profile)
  private val activitiesDefinitions     = new ScenarioActivitiesDefinitions(profile)

  private val processInsertQuery = processesTable returning
    processesTable.map(_.id) into ((item, id) => item.copy(id = id))
  private val commentInsertQuery = commentsDefinitions.table returning
    commentsDefinitions.table.map(_.id) into ((item, id) => item.copy(id = id))
  private val actionInsertQuery = processActionsDefinitions.table returning
    processActionsDefinitions.table.map(_.id) into ((item, id) => item.copy(id = id))

  private val scenarioActivityRepository = DbScenarioActivityRepository.create(testDbRef, clock)

  private val now: Timestamp   = Timestamp.from(Instant.now)
  private val user             = "John Doe"
  private val processVersionId = 5L

  "When data is present in old actions and comments tables" - {
    "migrate 100000 DEPLOY actions with comments to scenario_activities table" in {
      val (createdProcess, actionsBeingMigrated, activitiesAfterMigration) = run(
        for {
          process <- processInsertQuery += processEntity(user, now)
          _       <- processVersionsTable += processVersionEntity(process)
          comments <-
            commentInsertQuery ++= List
              .range(1L, 100001L)
              .map(id => commentEntity(process, id, s"Deployment: Very important change $id"))
          actions <-
            actionInsertQuery ++= comments.map(c => processActionEntity(process, ScenarioActionName.Deploy, Some(c.id)))
          _          <- migration.migrate
          activities <- activitiesDefinitions.scenarioActivitiesTable.result
        } yield (process, actions, activities)
      )

      actionsBeingMigrated.length shouldBe 100000
      activitiesAfterMigration.length shouldBe 100000

      val headActivity =
        activitiesAfterMigration.head
      val expectedOldCommentIdForHeadActivity =
        headActivity.comment.map(_.filter(_.isDigit).toLong).get
      val expectedActionIdForHeadActivity =
        actionsBeingMigrated.find(_.commentId.contains(expectedOldCommentIdForHeadActivity)).map(_.id).get

      headActivity shouldBe ScenarioActivityEntityData(
        id = headActivity.id,
        activityType = "SCENARIO_DEPLOYED",
        scenarioId = createdProcess.id.value,
        activityId = expectedActionIdForHeadActivity,
        userId = None,
        userName = user,
        impersonatedByUserId = None,
        impersonatedByUserName = None,
        lastModifiedByUserName = Some(user),
        lastModifiedAt = Some(now),
        createdAt = now,
        scenarioVersion = Some(processVersionId),
        comment = Some(s"Very important change $expectedOldCommentIdForHeadActivity"),
        attachmentId = None,
        finishedAt = Some(now),
        state = Some("FINISHED"),
        errorMessage = None,
        buildInfo = None,
        additionalProperties = AdditionalProperties.empty.properties.asJson.noSpaces,
      )
    }
    "migrate DEPLOY action with comment to scenario_activities table" in {
      testMigratingActionWithComment(
        scenarioActionName = ScenarioActionName.Deploy,
        actionComment = Some("Deployment: Deployment with scenario fix"),
        expectedActivity = (sid, sad, user, date, sv) =>
          ScenarioActivity.ScenarioDeployed(
            scenarioId = sid,
            scenarioActivityId = sad,
            user = user,
            date = date,
            scenarioVersionId = sv,
            comment = ScenarioComment.from("Deployment with scenario fix", user.name, date),
            result = DeploymentResult.Success(date),
          )
      )
    }
    "migrate CANCEL action with comment to scenario_activities table" in {
      testMigratingActionWithComment(
        scenarioActionName = ScenarioActionName.Cancel,
        actionComment = Some("Stop: I'm canceling this scenario, it causes problems"),
        expectedActivity = (sid, sad, user, date, sv) =>
          ScenarioActivity.ScenarioCanceled(
            scenarioId = sid,
            scenarioActivityId = sad,
            user = user,
            date = date,
            scenarioVersionId = sv,
            comment = ScenarioComment.from("I'm canceling this scenario, it causes problems", user.name, date),
            result = DeploymentResult.Success(date),
          )
      )
    }
    "migrate ARCHIVE action with comment to scenario_activities table" in {
      testMigratingActionWithComment(
        scenarioActionName = ScenarioActionName.Archive,
        actionComment = None,
        expectedActivity = (sid, sad, user, date, sv) =>
          ScenarioActivity.ScenarioArchived(
            scenarioId = sid,
            scenarioActivityId = sad,
            user = user,
            date = date,
            scenarioVersionId = sv,
          )
      )
    }
    "migrate UNARCHIVE action with comment to scenario_activities table" in {
      testMigratingActionWithComment(
        scenarioActionName = ScenarioActionName.UnArchive,
        actionComment = None,
        expectedActivity = (sid, sad, user, date, sv) =>
          ScenarioActivity.ScenarioUnarchived(
            scenarioId = sid,
            scenarioActivityId = sad,
            user = user,
            date = date,
            scenarioVersionId = sv,
          )
      )
    }
    "migrate PAUSE action with comment to scenario_activities table" in {
      testMigratingActionWithComment(
        scenarioActionName = ScenarioActionName.Pause,
        actionComment = Some("Paused because marketing campaign is paused for now"),
        expectedActivity = (sid, sad, user, date, sv) =>
          ScenarioActivity.ScenarioPaused(
            scenarioId = sid,
            scenarioActivityId = sad,
            user = user,
            date = date,
            scenarioVersionId = sv,
            comment = ScenarioComment.from("Paused because marketing campaign is paused for now", user.name, date),
            result = DeploymentResult.Success(date),
          )
      )
    }
    "migrate RENAME action with comment to scenario_activities table" in {
      val actionComment = "Rename: [marketing-campaign] -> [marketing-campaign-plus]"

      val scenario = run(
        for {
          process <- processInsertQuery += processEntity(user, now)
          _       <- processVersionsTable += processVersionEntity(process)
          comment <- commentInsertQuery += commentEntity(process, 1L, actionComment)
          _       <- actionInsertQuery += processActionEntity(process, ScenarioActionName.Rename, Some(comment.id))
          _       <- migration.migrate
          _       <- activitiesDefinitions.scenarioActivitiesTable.result
        } yield process
      )

      val entities = run(activitiesDefinitions.scenarioActivitiesTable.result)

      entities shouldBe Vector(
        ScenarioActivityEntityData(
          id = entities.head.id,
          activityType = "SCENARIO_NAME_CHANGED",
          scenarioId = scenario.id.value,
          activityId = entities.head.activityId,
          userId = None,
          userName = "John Doe",
          impersonatedByUserId = None,
          impersonatedByUserName = None,
          lastModifiedByUserName = Some("John Doe"),
          lastModifiedAt = entities.head.lastModifiedAt,
          createdAt = entities.head.createdAt,
          scenarioVersion = Some(5),
          comment = Some("Rename: [marketing-campaign] -> [marketing-campaign-plus]"),
          attachmentId = None,
          finishedAt = Some(now),
          state = Some("FINISHED"),
          errorMessage = None,
          buildInfo = None,
          additionalProperties = "{}"
        )
      )
    }
    "migrate custom action 'run now' with comment to scenario_activities table" in {
      testMigratingActionWithComment(
        scenarioActionName = InstantBatchCustomAction.name,
        actionComment = Some("Run now: Deployed at the request of business"),
        expectedActivity = (sid, sad, user, date, sv) =>
          ScenarioActivity.PerformedSingleExecution(
            scenarioId = sid,
            scenarioActivityId = sad,
            user = user,
            date = date,
            scenarioVersionId = sv,
            comment = ScenarioComment.from("Deployed at the request of business", user.name, date),
            result = DeploymentResult.Success(date),
          )
      )
    }
    "migrate custom action with comment to scenario_activities table" in {
      testMigratingActionWithComment(
        scenarioActionName = ScenarioActionName("special action"),
        actionComment = Some("Special action needed to be executed"),
        expectedActivity = (sid, sad, user, date, sv) =>
          ScenarioActivity.CustomAction(
            scenarioId = sid,
            scenarioActivityId = sad,
            user = user,
            date = date,
            scenarioVersionId = sv,
            actionName = "special action",
            comment = ScenarioComment.from("Special action needed to be executed", user.name, date),
            result = DeploymentResult.Success(date),
          )
      )
    }
    "migrate standalone comments (not assigned to any action) to scenario_activities table" in {
      val (process, entities) = run(
        for {
          process  <- processInsertQuery += processEntity(user, now)
          _        <- processVersionsTable += processVersionEntity(process)
          _        <- commentInsertQuery += commentEntity(process, 1L, "ABC1")
          _        <- commentInsertQuery += commentEntity(process, 2L, "ABC2")
          _        <- migration.migrate
          entities <- activitiesDefinitions.scenarioActivitiesTable.result
        } yield (process, entities)
      )
      val activities = run(scenarioActivityRepository.findActivities(process.id))

      activities shouldBe Vector(
        ScenarioActivity.CommentAdded(
          scenarioId = ScenarioId(process.id.value),
          scenarioActivityId = activities(0).scenarioActivityId,
          user = ScenarioUser(None, UserName("John Doe"), None, None),
          date = now.toInstant,
          scenarioVersionId = Some(ScenarioVersionId(processVersionId)),
          comment = ScenarioComment.from("ABC1", UserName(user), now.toInstant)
        ),
        ScenarioActivity.CommentAdded(
          scenarioId = ScenarioId(process.id.value),
          scenarioActivityId = activities(1).scenarioActivityId,
          user = ScenarioUser(None, UserName("John Doe"), None, None),
          date = now.toInstant,
          scenarioVersionId = Some(ScenarioVersionId(processVersionId)),
          comment = ScenarioComment.from("ABC2", UserName(user), now.toInstant)
        )
      )
    }
  }

  private def testMigratingActionWithComment(
      scenarioActionName: ScenarioActionName,
      actionComment: Option[String],
      expectedActivity: (
          ScenarioId,
          ScenarioActivityId,
          ScenarioUser,
          Instant,
          Option[ScenarioVersionId]
      ) => ScenarioActivity,
  ): Unit = {
    val (process, action) = run(
      for {
        process <- processInsertQuery += processEntity(user, now)
        _       <- processVersionsTable += processVersionEntity(process)
        comment <- actionComment.map(commentInsertQuery += commentEntity(process, 1L, _)) match {
          case Some(commentEntity) => commentEntity.map(Some(_))
          case None                => DBIO.successful(None)
        }
        action <- actionInsertQuery += processActionEntity(process, scenarioActionName, comment.map(_.id))
        _      <- migration.migrate
        _      <- activitiesDefinitions.scenarioActivitiesTable.result
      } yield (process, action)
    )
    val activities = run(scenarioActivityRepository.findActivities(process.id))

    activities shouldBe Vector(
      expectedActivity(
        ScenarioId(process.id.value),
        ScenarioActivityId(action.id),
        ScenarioUser(None, UserName("John Doe"), None, None),
        now.toInstant,
        Some(ScenarioVersionId(processVersionId)),
      )
    )
  }

  private def run[T](action: DBIO[T]): T = Await.result(runner.run(action), Duration.Inf)

  private def processEntity(user: String, timestamp: Timestamp) = ProcessEntityData(
    id = ProcessId(-1L),
    name = ProcessName("2023_Q1_1234_STREAMING_SERVICE"),
    processCategory = "test-category",
    description = None,
    processingType = "BatchPeriodic",
    isFragment = false,
    isArchived = false,
    createdAt = timestamp,
    createdBy = user,
    impersonatedByIdentity = None,
    impersonatedByUsername = None
  )

  private def processVersionEntity(
      processEntity: ProcessEntityData,
  ) =
    ProcessVersionEntityData(
      id = VersionId(processVersionId),
      processId = processEntity.id,
      json = Some(
        CanonicalProcess(
          metaData = MetaData(
            "test-id",
            ProcessAdditionalFields(
              description = None,
              properties = Map.empty,
              metaDataType = RequestResponseMetaData.typeName,
              showDescription = true
            )
          ),
          nodes = List.empty,
          additionalBranches = List.empty
        )
      ),
      createDate = now,
      user = user,
      modelVersion = None,
      componentsUsages = Some(ScenarioComponentsUsages.Empty),
    )

  private def commentEntity(
      processEntity: ProcessEntityData,
      commentId: Long,
      content: String,
  ) =
    CommentEntityData(
      id = commentId,
      processId = processEntity.id.value,
      processVersionId = processVersionId,
      content = content,
      user = user,
      impersonatedByIdentity = None,
      impersonatedByUsername = None,
      createDate = now,
    )

  private def processActionEntity(
      processEntity: ProcessEntityData,
      scenarioActionName: ScenarioActionName,
      commentId: Option[Long],
  ) = ProcessActionEntityData(
    id = UUID.randomUUID(),
    processId = processEntity.id.value,
    processVersionId = Some(processVersionId),
    user = user,
    impersonatedByIdentity = None,
    impersonatedByUsername = None,
    createdAt = now,
    performedAt = Some(now),
    actionName = scenarioActionName.value,
    state = "FINISHED",
    failureMessage = None,
    commentId = commentId,
    buildInfo = None
  )

}
