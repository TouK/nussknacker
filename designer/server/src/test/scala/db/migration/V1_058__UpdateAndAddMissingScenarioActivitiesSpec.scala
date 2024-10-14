package db.migration

import db.migration.V1_058__UpdateAndAddMissingScenarioActivitiesDefinition.Migration
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.ScenarioActivity.CommentAdded
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.api.{MetaData, ProcessAdditionalFields, RequestResponseMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.restmodel.component.ScenarioComponentsUsages
import pl.touk.nussknacker.test.base.db.WithHsqlDbTesting
import pl.touk.nussknacker.test.base.it.NuItTest
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig
import pl.touk.nussknacker.test.utils.domain.TestFactory.newDBIOActionRunner
import pl.touk.nussknacker.ui.db.NuTables
import pl.touk.nussknacker.ui.db.entity.{ProcessEntityData, ProcessVersionEntityData}
import pl.touk.nussknacker.ui.process.repository.activities.DbScenarioActivityRepository
import pl.touk.nussknacker.ui.security.api.{CommonUser, LoggedUser}
import slick.jdbc.{HsqldbProfile, JdbcProfile}

import java.sql.Timestamp
import java.time.Instant
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class V1_058__UpdateAndAddMissingScenarioActivitiesSpec
    extends AnyFreeSpecLike
    with Matchers
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithHsqlDbTesting
    with NuTables {

  override protected val profile: JdbcProfile = HsqldbProfile

  import profile.api._

  private val runner = newDBIOActionRunner(testDbRef)

  private val migration = new Migration(HsqldbProfile)

  private val processInsertQuery = processesTable returning
    processesTable.map(_.id) into ((item, id) => item.copy(id = id))

  private val scenarioActivityRepository = new DbScenarioActivityRepository(testDbRef, clock)

  private val now: Timestamp = Timestamp.from(Instant.now)
  private val user           = "Test User"

  "When there are activities to migrate" - {
    "migrate incoming migration stored as COMMENT_ADDED, add SCENARIO_CREATED for version 1, add SCENARIO_UPDATED for version 2" in {
      implicit val loggedUser: LoggedUser = CommonUser("testUser", "Test User")

      val process = run(processInsertQuery += processEntity(loggedUser.username, now))
      run(processVersionsTable += processVersionEntity(process, 1L))
      run(processVersionsTable += processVersionEntity(process, 2L))
      run(
        scenarioActivityRepository.addActivity(
          CommentAdded(
            scenarioId = ScenarioId(process.id.value),
            scenarioActivityId = ScenarioActivityId.random,
            user = ScenarioUser(
              id = Some(UserId(loggedUser.id)),
              name = UserName(loggedUser.username),
              impersonatedByUserId = None,
              impersonatedByUserName = None
            ),
            date = Instant.now,
            scenarioVersionId = None,
            comment = ScenarioComment.Available(
              comment = "Scenario migrated from TEST_ENV by test env user",
              lastModifiedByUserName = UserName(loggedUser.username),
              lastModifiedAt = Instant.now
            )
          )
        )
      )
      run(migration.migrate)
      val activities = run(scenarioActivityRepository.findActivities(process.id)).toList
      activities shouldBe List(
        ScenarioActivity.ScenarioModified(
          scenarioId = ScenarioId(process.id.value),
          scenarioActivityId = activities(0).scenarioActivityId,
          user = ScenarioUser(None, UserName("Test User"), None, None),
          date = activities(0).date,
          previousScenarioVersionId = None,
          scenarioVersionId = Some(ScenarioVersionId(2)),
          comment = ScenarioComment.Deleted(UserName("Test User"), activities(0).date)
        ),
        ScenarioActivity.ScenarioCreated(
          scenarioId = ScenarioId(process.id.value),
          scenarioActivityId = activities(1).scenarioActivityId,
          user = ScenarioUser(None, UserName("Test User"), None, None),
          date = activities(1).date,
          scenarioVersionId = Some(ScenarioVersionId(1))
        ),
        ScenarioActivity.IncomingMigration(
          scenarioId = ScenarioId(process.id.value),
          scenarioActivityId = activities(2).scenarioActivityId,
          user = ScenarioUser(Some(UserId("testUser")), UserName("Test User"), None, None),
          date = activities(2).date,
          scenarioVersionId = None,
          sourceEnvironment = Environment("TEST_ENV"),
          sourceUser = UserName("test env user"),
          sourceScenarioVersionId = None,
          targetEnvironment = None
        )
      )
    }
    "migrate incoming migration stored as SCENARIO_MODIFIED with comment" in {
      implicit val loggedUser: LoggedUser = CommonUser("testUser", "Test User")

      val process = run(processInsertQuery += processEntity(loggedUser.username, now))
      run(processVersionsTable += processVersionEntity(process, 1L))
      run(
        scenarioActivityRepository.addActivity(
          ScenarioActivity.ScenarioModified(
            scenarioId = ScenarioId(process.id.value),
            scenarioActivityId = ScenarioActivityId.random,
            user = ScenarioUser(
              id = Some(UserId(loggedUser.id)),
              name = UserName(loggedUser.username),
              impersonatedByUserId = None,
              impersonatedByUserName = None
            ),
            date = Instant.now,
            previousScenarioVersionId = None,
            scenarioVersionId = None,
            comment = ScenarioComment.Available(
              comment = "Scenario migrated from TEST_ENV by test env user",
              lastModifiedByUserName = UserName(loggedUser.username),
              lastModifiedAt = Instant.now
            )
          )
        )
      )
      run(migration.migrate)
      val activities = run(scenarioActivityRepository.findActivities(process.id)).toList
      activities shouldBe List(
        ScenarioActivity.ScenarioCreated(
          scenarioId = ScenarioId(process.id.value),
          scenarioActivityId = activities(0).scenarioActivityId,
          user = ScenarioUser(None, UserName("Test User"), None, None),
          date = activities(0).date,
          scenarioVersionId = Some(ScenarioVersionId(1))
        ),
        ScenarioActivity.IncomingMigration(
          scenarioId = ScenarioId(process.id.value),
          scenarioActivityId = activities(1).scenarioActivityId,
          user = ScenarioUser(Some(UserId("testUser")), UserName("Test User"), None, None),
          date = activities(1).date,
          scenarioVersionId = None,
          sourceEnvironment = Environment("TEST_ENV"),
          sourceUser = UserName("test env user"),
          sourceScenarioVersionId = None,
          targetEnvironment = None
        )
      )
    }
    "migrate automatic update stored as COMMENT_ADDED" in {
      implicit val loggedUser: LoggedUser = CommonUser("testUser", "Test User")

      val process = run(processInsertQuery += processEntity(loggedUser.username, now))
      run(processVersionsTable += processVersionEntity(process, 1L))
      run(
        scenarioActivityRepository.addActivity(
          ScenarioActivity.CommentAdded(
            scenarioId = ScenarioId(process.id.value),
            scenarioActivityId = ScenarioActivityId.random,
            user = ScenarioUser(
              id = Some(UserId(loggedUser.id)),
              name = UserName(loggedUser.username),
              impersonatedByUserId = None,
              impersonatedByUserName = None
            ),
            date = Instant.now,
            scenarioVersionId = None,
            comment = ScenarioComment.Available(
              comment = "Migrations applied: feature A\\nfeature B\\nfeature C",
              lastModifiedByUserName = UserName(loggedUser.username),
              lastModifiedAt = Instant.now
            )
          )
        )
      )
      run(migration.migrate)
      val activities = run(scenarioActivityRepository.findActivities(process.id)).toList
      activities shouldBe List(
        ScenarioActivity.ScenarioCreated(
          scenarioId = ScenarioId(process.id.value),
          scenarioActivityId = activities(0).scenarioActivityId,
          user = ScenarioUser(None, UserName("Test User"), None, None),
          date = activities(0).date,
          scenarioVersionId = Some(ScenarioVersionId(1))
        ),
        ScenarioActivity.AutomaticUpdate(
          scenarioId = ScenarioId(process.id.value),
          scenarioActivityId = activities(1).scenarioActivityId,
          user = ScenarioUser(Some(UserId("testUser")), UserName("Test User"), None, None),
          date = activities(1).date,
          scenarioVersionId = None,
          changes = """feature A
              |feature B
              |feature C""".stripMargin,
        )
      )
    }
    "migrate automatic update stored as SCENARIO_MODIFIED with comment" in {
      implicit val loggedUser: LoggedUser = CommonUser("testUser", "Test User")

      val process = run(processInsertQuery += processEntity(loggedUser.username, now))
      run(processVersionsTable += processVersionEntity(process, 1L))
      run(
        scenarioActivityRepository.addActivity(
          ScenarioActivity.ScenarioModified(
            scenarioId = ScenarioId(process.id.value),
            scenarioActivityId = ScenarioActivityId.random,
            user = ScenarioUser(
              id = Some(UserId(loggedUser.id)),
              name = UserName(loggedUser.username),
              impersonatedByUserId = None,
              impersonatedByUserName = None
            ),
            date = Instant.now,
            previousScenarioVersionId = None,
            scenarioVersionId = None,
            comment = ScenarioComment.Available(
              comment = "Migrations applied: feature A\\nfeature B\\nfeature C",
              lastModifiedByUserName = UserName(loggedUser.username),
              lastModifiedAt = Instant.now
            )
          )
        )
      )
      run(migration.migrate)
      val activities = run(scenarioActivityRepository.findActivities(process.id)).toList
      activities shouldBe List(
        ScenarioActivity.ScenarioCreated(
          scenarioId = ScenarioId(process.id.value),
          scenarioActivityId = activities(0).scenarioActivityId,
          user = ScenarioUser(None, UserName("Test User"), None, None),
          date = activities(0).date,
          scenarioVersionId = Some(ScenarioVersionId(1))
        ),
        ScenarioActivity.AutomaticUpdate(
          scenarioId = ScenarioId(process.id.value),
          scenarioActivityId = activities(1).scenarioActivityId,
          user = ScenarioUser(Some(UserId("testUser")), UserName("Test User"), None, None),
          date = activities(1).date,
          scenarioVersionId = None,
          changes = """feature A
              |feature B
              |feature C""".stripMargin,
        )
      )
    }
    "migrate scenario name change stored with legacy comment instead of properties" in {
      implicit val loggedUser: LoggedUser = CommonUser("testUser", "Test User")

      val process = run(processInsertQuery += processEntity(loggedUser.username, now))
      run(processVersionsTable += processVersionEntity(process, 1L))
      run(
        scenarioActivityRepository.addActivity(
          ScenarioActivity.CommentAdded(
            scenarioId = ScenarioId(process.id.value),
            scenarioActivityId = ScenarioActivityId.random,
            user = ScenarioUser(
              id = Some(UserId(loggedUser.id)),
              name = UserName(loggedUser.username),
              impersonatedByUserId = None,
              impersonatedByUserName = None
            ),
            date = Instant.now,
            scenarioVersionId = None,
            comment = ScenarioComment.Available(
              comment = "Rename: [oldUglyName] -> [newPrettyName]",
              lastModifiedByUserName = UserName(loggedUser.username),
              lastModifiedAt = Instant.now
            )
          )
        )
      )
      run(migration.migrate)
      val activities = run(scenarioActivityRepository.findActivities(process.id)).toList
      activities shouldBe List(
        ScenarioActivity.ScenarioCreated(
          scenarioId = ScenarioId(process.id.value),
          scenarioActivityId = activities(0).scenarioActivityId,
          user = ScenarioUser(None, UserName("Test User"), None, None),
          date = activities(0).date,
          scenarioVersionId = Some(ScenarioVersionId(1))
        ),
        ScenarioActivity.ScenarioNameChanged(
          scenarioId = ScenarioId(process.id.value),
          scenarioActivityId = activities(1).scenarioActivityId,
          user = ScenarioUser(Some(UserId("testUser")), UserName("Test User"), None, None),
          date = activities(1).date,
          scenarioVersionId = None,
          oldName = "oldUglyName",
          newName = "newPrettyName",
        )
      )
    }
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
      processVersionId: Long,
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

}
