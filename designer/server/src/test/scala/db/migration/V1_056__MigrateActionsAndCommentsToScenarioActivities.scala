package db.migration

import db.migration.V1_055__CreateScenarioActivitiesDefinition.ScenarioActivityEntityData
import db.migration.V1_056__MigrateActionsAndCommentsToScenarioActivitiesDefinition._
import io.circe.syntax.EncoderOps
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.api.{MetaData, ProcessAdditionalFields, RequestResponseMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.restmodel.component.ScenarioComponentsUsages
import pl.touk.nussknacker.test.base.db.WithHsqlDbTesting
import pl.touk.nussknacker.test.base.it.NuItTest
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig
import pl.touk.nussknacker.test.utils.domain.TestFactory.newDBIOActionRunner
import pl.touk.nussknacker.ui.db.NuTables
import pl.touk.nussknacker.ui.db.entity.{AdditionalProperties, ProcessEntityData, ProcessVersionEntityData}
import slick.jdbc.{HsqldbProfile, JdbcProfile}

import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class V1_056__MigrateActionsAndCommentsToScenarioActivities
    extends AnyFreeSpecLike
    with Matchers
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithHsqlDbTesting
    with NuTables {

  override protected val profile: JdbcProfile = HsqldbProfile

  "When data is present in old actions and comments tables" - {
    "migrate data to scenario_activities table" in {
      import HsqldbProfile.api._
      val runner = newDBIOActionRunner(testDbRef)

      val migration                 = new Migration(HsqldbProfile)
      val processActionsDefinitions = new ProcessActionsDefinitions(profile)
      val commentsDefinitions       = new CommentsDefinitions(profile)

      val now: Timestamp = Timestamp.from(Instant.now)
      val user           = "John Doe"
      val versionId      = VersionId(5L)
      val actionId       = UUID.randomUUID()
      val commentId      = 765L

      val processInsertQuery = processesTable returning processesTable.map(_.id) into ((item, id) => item.copy(id = id))

      val processEntity = ProcessEntityData(
        id = ProcessId(-1L),
        name = ProcessName("2024_Q3_6917_NETFLIX"),
        processCategory = "test-category",
        description = None,
        processingType = "BatchPeriodic",
        isFragment = false,
        isArchived = false,
        createdAt = now,
        createdBy = user,
        impersonatedByIdentity = None,
        impersonatedByUsername = None
      )

      def processVersionEntity(processEntity: ProcessEntityData) = ProcessVersionEntityData(
        id = versionId,
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

      def commentEntity(processEntity: ProcessEntityData) = CommentEntityData(
        id = commentId,
        processId = processEntity.id.value,
        processVersionId = versionId.value,
        content = "Very important change",
        user = user,
        impersonatedByIdentity = None,
        impersonatedByUsername = None,
        createDate = now,
      )

      def processActionEntity(processEntity: ProcessEntityData) = ProcessActionEntityData(
        id = actionId,
        processId = processEntity.id.value,
        processVersionId = Some(versionId.value),
        user = user,
        impersonatedByIdentity = None,
        impersonatedByUsername = None,
        createdAt = now,
        performedAt = None,
        actionName = ScenarioActionName.Deploy.value,
        state = "IN_PROGRESS",
        failureMessage = None,
        commentId = Some(commentId),
        buildInfo = None
      )

      val dbOperations = for {
        process <- processInsertQuery += processEntity
        _       <- processVersionsTable += processVersionEntity(process)
        _       <- commentsDefinitions.table += commentEntity(process)
        _       <- processActionsDefinitions.table += processActionEntity(process)
        result  <- migration.migrateActions
      } yield result._1

      val activitiesCreatedDuringMigration = Await.result(runner.run(dbOperations), Duration.Inf)

      activitiesCreatedDuringMigration shouldBe
        List(
          ScenarioActivityEntityData(
            id = -1,
            activityType = "SCENARIO_DEPLOYED",
            scenarioId = 1,
            activityId = actionId,
            userId = None,
            userName = user,
            impersonatedByUserId = None,
            impersonatedByUserName = None,
            lastModifiedByUserName = Some(user),
            createdAt = now,
            scenarioVersion = Some(versionId.value),
            comment = Some("Very important change"),
            attachmentId = None,
            finishedAt = None,
            state = Some("IN_PROGRESS"),
            errorMessage = None,
            buildInfo = None,
            additionalProperties = AdditionalProperties.empty.properties.asJson.noSpaces,
          )
        )

    }
  }

}
