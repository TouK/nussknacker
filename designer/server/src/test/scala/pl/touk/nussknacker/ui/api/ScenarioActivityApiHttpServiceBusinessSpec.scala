package pl.touk.nussknacker.ui.api

import db.util.DBIOActionInstances.DB
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.test.{
  NuRestAssureExtensions,
  NuRestAssureMatchers,
  PatientScalaFutures,
  RestAssuredVerboseLoggingIfValidationFails
}
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{
  WithBusinessCaseRestAssuredUsersExtensions,
  WithMockableDeploymentManager,
  WithSimplifiedDesignerConfig
}
import pl.touk.nussknacker.test.processes.WithScenarioActivitySpecAsserts
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner
import pl.touk.nussknacker.ui.process.repository.activities.DbScenarioActivityRepository

import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatterBuilder
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class ScenarioActivityApiHttpServiceBusinessSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithSimplifiedConfigScenarioHelper
    with WithMockableDeploymentManager
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureExtensions
    with WithScenarioActivitySpecAsserts
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails
    with PatientScalaFutures {

  private val scenarioActivityRepository   = DbScenarioActivityRepository.createWithoutAuditDecorator(testDbRef, clock)
  private val dbioRunner: DBIOActionRunner = DBIOActionRunner(testDbRef)

  private val exampleScenarioName = UUID.randomUUID().toString
  private val commentContent      = "test message"
  private val wrongScenarioName   = "wrongProcessName"
  private val fileContent         = "very important content"
  private val fileName            = "important_file.txt"

  private val exampleScenario = ScenarioBuilder
    .streaming(exampleScenarioName)
    .source("sourceId", "barSource")
    .emptySink("sinkId", "barSink")

  private val otherExampleScenario = ScenarioBuilder
    .streaming(UUID.randomUUID().toString)
    .source("sourceId", "barSource")
    .emptySink("sinkId", "barSink")

  "Deprecated scenario activity endpoint when" - {
    "return empty comments and attachment for existing process without them" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          s"""
             |{
             |  "comments": [],
             |  "attachments": []
             |}
             |""".stripMargin
        )
    }
    "return 404 for no existing scenario" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/processes/$wrongScenarioName/activity")
        .Then()
        .statusCode(404)
        .equalsPlainBody(s"No scenario $wrongScenarioName found")
    }
  }

  "Deprecated scenario add comment endpoint when" - {
    "add comment in existing scenario" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .plainBody(commentContent)
        .post(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/1/activity/comments")
        .Then()
        .statusCode(200)
        .verifyApplicationState {
          verifyCommentExists(
            scenarioName = exampleScenarioName,
            commentContent = commentContent,
            commentUser = "allpermuser"
          )
        }
    }
    "return 404 for no existing scenario" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .plainBody(commentContent)
        .post(s"$nuDesignerHttpAddress/api/processes/$wrongScenarioName/1/activity/comments")
        .Then()
        .statusCode(404)
        .equalsPlainBody(s"No scenario $wrongScenarioName found")
    }
  }

  "Deprecated scenario remove comment endpoint when" - {
    "remove comment in existing scenario" in {
      val commentId = given()
        .applicationState {
          createSavedScenario(exampleScenario)
          createComment(scenarioName = exampleScenarioName, commentContent = commentContent)
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity")
        .Then()
        .extractLong("comments[0].id")

      given()
        .when()
        .basicAuthAllPermUser()
        .delete(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity/comments/$commentId")
        .Then()
        .statusCode(200)
        .verifyApplicationState {
          verifyEmptyCommentsAndAttachments(exampleScenarioName)
        }
    }
    "return 500 for no existing comment" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .delete(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity/comments/1")
        .Then()
        .statusCode(500)
        .equalsPlainBody("Unable to delete comment with id: 1")
    }
    "return 404 for no existing scenario" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .delete(s"$nuDesignerHttpAddress/api/processes/$wrongScenarioName/activity/comments/1")
        .Then()
        .statusCode(404)
        .equalsPlainBody(s"No scenario $wrongScenarioName found")
    }
  }

  "The scenario add attachment endpoint when" - {
    "add attachment to existing scenario" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .streamBody(fileContent = fileContent, fileName = fileName)
        .post(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/1/activity/attachments")
        .Then()
        .statusCode(200)
        .verifyApplicationState {
          verifyAttachmentsExists(exampleScenarioName)
        }
    }
    "handle attachments with the same name" in {
      val fileContent1 = "very important content1"
      val fileContent2 = "very important content2"
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
          createAttachment(scenarioName = exampleScenarioName, fileContent = fileContent1, fileName = fileName)
          createAttachment(scenarioName = exampleScenarioName, fileContent = fileContent2, fileName = fileName)
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity")
        .Then()
        .body(
          matchJsonWithRegexValues(
            s"""
               |{
               |  "comments": [],
               |  "attachments": [
               |    {
               |      "id": "${regexes.digitsRegex}",
               |      "processVersionId": 1,
               |      "fileName": "important_file.txt",
               |      "user": "allpermuser",
               |      "createDate": "${regexes.zuluDateRegex}"
               |    },
               |    {
               |      "id": "${regexes.digitsRegex}",
               |      "processVersionId": 1,
               |      "fileName": "important_file.txt",
               |      "user": "allpermuser",
               |      "createDate": "${regexes.zuluDateRegex}"
               |    }
               |  ]
               |}
               |""".stripMargin
          )
        )
    }
    "return 404 for no existing scenario" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .streamBody(fileContent = fileContent, fileName = fileName)
        .post(s"$nuDesignerHttpAddress/api/processes/$wrongScenarioName/1/activity/attachments")
        .Then()
        .statusCode(404)
        .equalsPlainBody(s"No scenario $wrongScenarioName found")
    }
  }

  "The scenario delete attachment endpoint when" - {
    "add and delete attachment to existing scenario" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .streamBody(fileContent = fileContent, fileName = fileName)
        .post(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/1/activity/attachments")
        .Then()
        .statusCode(200)

      val attachmentId = verifyAttachmentAddedActivityExists(
        user = "allpermuser",
        scenarioName = exampleScenarioName,
        fileIdPresent = true,
        filename = fileName,
        fileStatus = "AVAILABLE",
        overrideDisplayableName = fileName
      ).getAddedAttachmentId

      given()
        .when()
        .basicAuthAllPermUser()
        .delete(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity/attachments/$attachmentId")
        .Then()
        .statusCode(200)
        .verifyApplicationState {
          verifyAttachmentAddedActivityExists(
            user = "allpermuser",
            scenarioName = exampleScenarioName,
            fileIdPresent = false,
            filename = fileName,
            fileStatus = "DELETED",
            overrideDisplayableName = "File removed"
          )
        }
    }
    "return 404 for no existing attachment" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .streamBody(fileContent = fileContent, fileName = fileName)
        .post(s"$nuDesignerHttpAddress/api/processes/$wrongScenarioName/1/activity/attachments")
        .Then()
        .statusCode(404)
        .equalsPlainBody(s"No scenario $wrongScenarioName found")
    }
    "return 404 for no existing scenario" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .streamBody(fileContent = fileContent, fileName = fileName)
        .post(s"$nuDesignerHttpAddress/api/processes/$wrongScenarioName/1/activity/attachments")
        .Then()
        .statusCode(404)
        .equalsPlainBody(s"No scenario $wrongScenarioName found")
    }
  }

  "The scenario download attachment endpoint when" - {
    "download existing attachment" in {
      val attachmentId = given()
        .applicationState {
          createSavedScenario(exampleScenario)
          createAttachment(scenarioName = exampleScenarioName, fileContent = fileContent)
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity")
        .Then()
        .extractLong("attachments[0].id")

      given()
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity/attachments/$attachmentId")
        .Then()
        .statusCode(200)
        .equalsPlainBody(fileContent)
    }
    "not return existing attachment not connected to the scenario" in {
      val notRelevantScenarioId = given()
        .applicationState {
          createSavedScenario(exampleScenario)
          createAttachment(scenarioName = exampleScenarioName, fileContent = fileContent)
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity")
        .Then()
        .extractLong("attachments[0].id")

      given()
        .applicationState {
          createSavedScenario(otherExampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .get(
          s"$nuDesignerHttpAddress/api/processes/${otherExampleScenario.name}/activity/" +
            s"attachments/$notRelevantScenarioId"
        )
        .Then()
        .statusCode(200)
        .equalsPlainBody("")
    }
    "return empty body for no existing attachment" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity/attachments/1")
        .Then()
        .statusCode(200)
        .equalsPlainBody("")
    }
    "return 404 for no existing scenario" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/processes/$wrongScenarioName/activity/attachments/1")
        .Then()
        .statusCode(404)
        .equalsPlainBody(s"No scenario $wrongScenarioName found")
    }
  }

  "The scenario activity endpoint when" - {
    "return only SCENARIO_CREATED activity for existing process that has only been created" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity/activities")
        .Then()
        .statusCode(200)
        .body(
          matchJsonWithRegexValues(
            s"""
               |{
               |  "activities": [
               |    {
               |      "id": "${regexes.looseUuidRegex}",
               |      "user": "admin",
               |      "date": "${regexes.zuluDateRegex}",
               |      "scenarioVersionId": 1,
               |      "additionalFields": [],
               |      "type": "SCENARIO_CREATED"
               |     }
               |  ]
               |}
               |""".stripMargin
          )
        )
    }
    // todo: add test, that will verify limiting the number of scheduling-related activities returned by the API
    "return PERFORMED_SCHEDULED_EXECUTION activities with various execution details" in {
      val processId = createSavedScenario(exampleScenario)
      val now       = Instant.now

      val activity1 = ScenarioActivity.PerformedScheduledExecution(
        scenarioId = ScenarioId(processId.value),
        scenarioActivityId = ScenarioActivityId.random,
        user = ScenarioUser(None, UserName("Nussknacker"), None, None),
        date = now.plusSeconds(1),
        scenarioVersionId = Some(ScenarioVersionId(1)),
        dateFinished = now.plusSeconds(1),
        scheduleName = "schedule1",
        scheduledExecutionStatus = ScheduledExecutionStatus.Finished,
        createdAt = now.plusSeconds(1),
        retriesLeft = None,
        nextRetryAt = None
      )

      val activity2 = ScenarioActivity.PerformedScheduledExecution(
        scenarioId = ScenarioId(processId.value),
        scenarioActivityId = ScenarioActivityId.random,
        user = ScenarioUser(None, UserName("Nussknacker"), None, None),
        date = now.plusSeconds(2),
        scenarioVersionId = Some(ScenarioVersionId(1)),
        dateFinished = now.plusSeconds(2),
        scheduleName = "schedule1",
        scheduledExecutionStatus = ScheduledExecutionStatus.DeploymentWillBeRetried,
        createdAt = now.plusSeconds(2),
        retriesLeft = Some(2),
        nextRetryAt = Some(now.plusSeconds(3))
      )

      val activity3 = ScenarioActivity.PerformedScheduledExecution(
        scenarioId = ScenarioId(processId.value),
        scenarioActivityId = ScenarioActivityId.random,
        user = ScenarioUser(None, UserName("Nussknacker"), None, None),
        date = now.plusSeconds(3),
        scenarioVersionId = Some(ScenarioVersionId(1)),
        dateFinished = now.plusSeconds(3),
        scheduleName = "schedule1",
        scheduledExecutionStatus = ScheduledExecutionStatus.Failed,
        createdAt = now.plusSeconds(3),
        retriesLeft = None,
        nextRetryAt = None,
      )

      val activity4 = ScenarioActivity.PerformedScheduledExecution(
        scenarioId = ScenarioId(processId.value),
        scenarioActivityId = ScenarioActivityId.random,
        user = ScenarioUser(None, UserName("Nussknacker"), None, None),
        date = now.plusSeconds(4),
        scenarioVersionId = Some(ScenarioVersionId(1)),
        dateFinished = now.plusSeconds(4),
        scheduleName = "schedule1",
        scheduledExecutionStatus = ScheduledExecutionStatus.DeploymentFailed,
        createdAt = now.plusSeconds(4),
        retriesLeft = None,
        nextRetryAt = None,
      )

      dbioValue(scenarioActivityRepository.addActivity(activity1))
      dbioValue(scenarioActivityRepository.addActivity(activity2))
      dbioValue(scenarioActivityRepository.addActivity(activity3))
      dbioValue(scenarioActivityRepository.addActivity(activity4))

      given()
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity/activities")
        .Then()
        .statusCode(200)
        .body(
          matchJsonWithRegexValues(
            s"""
               |{
               |    "activities": [
               |        {
               |            "id": "${regexes.looseUuidRegex}",
               |            "user": "admin",
               |            "date": "${regexes.zuluDateRegex}",
               |            "scenarioVersionId": 1,
               |            "additionalFields": [
               |
               |            ],
               |            "type": "SCENARIO_CREATED"
               |        },
               |        {
               |            "id": "${regexes.looseUuidRegex}",
               |            "user": "Nussknacker",
               |            "date": "${regexes.zuluDateRegex}",
               |            "scenarioVersionId": 1,
               |            "additionalFields": [
               |                {
               |                    "name": "status",
               |                    "value": "Data processing finished"
               |                },
               |                {
               |                    "name": "created",
               |                    "value": "${activity1.createdAt}",
               |                    "isDate": true
               |                },
               |                {
               |                    "name": "started",
               |                    "value": "${toStringWith6DigitsOfMillis(activity1.date)}",
               |                    "isDate": true
               |                },
               |                {
               |                    "name": "finished",
               |                    "value": "${toStringWith6DigitsOfMillis(activity1.dateFinished)}",
               |                    "isDate": true
               |                }
               |            ],
               |            "type": "PERFORMED_SCHEDULED_EXECUTION"
               |        },
               |        {
               |            "id": "${regexes.looseUuidRegex}",
               |            "user": "Nussknacker",
               |            "date": "${regexes.zuluDateRegex}",
               |            "scenarioVersionId": 1,
               |            "additionalFields": [
               |                {
               |                    "name": "status",
               |                    "value": "Deployment will be retried"
               |                },
               |                {
               |                    "name": "created",
               |                    "value": "${activity2.createdAt}",
               |                    "isDate": true
               |                },
               |                {
               |                    "name": "started",
               |                    "value": "${toStringWith6DigitsOfMillis(activity2.date)}",
               |                    "isDate": true
               |                },
               |                {
               |                    "name": "finished",
               |                    "value": "${toStringWith6DigitsOfMillis(activity2.dateFinished)}",
               |                    "isDate": true
               |                },
               |                {
               |                    "name": "retriesLeft",
               |                    "value": "2"
               |                },
               |                {
               |                    "name": "nextRetryAt",
               |                    "value": "${activity2.nextRetryAt.get}",
               |                    "isDate": true
               |                }
               |            ],
               |            "type": "PERFORMED_SCHEDULED_EXECUTION"
               |        },
               |        {
               |            "id": "${regexes.looseUuidRegex}",
               |            "user": "Nussknacker",
               |            "date": "${regexes.zuluDateRegex}",
               |            "scenarioVersionId": 1,
               |            "additionalFields": [
               |                {
               |                    "name": "status",
               |                    "value": "Data processing failed"
               |                },
               |                {
               |                    "name": "created",
               |                    "value": "${activity3.createdAt}",
               |                    "isDate": true
               |                },
               |                {
               |                    "name": "started",
               |                    "value": "${toStringWith6DigitsOfMillis(activity3.date)}",
               |                    "isDate": true
               |                },
               |                {
               |                    "name": "finished",
               |                    "value": "${toStringWith6DigitsOfMillis(activity3.dateFinished)}",
               |                    "isDate": true
               |                }
               |            ],
               |            "type": "PERFORMED_SCHEDULED_EXECUTION"
               |        },
               |        {
               |            "id": "${regexes.looseUuidRegex}",
               |            "user": "Nussknacker",
               |            "date": "${regexes.zuluDateRegex}",
               |            "scenarioVersionId": 1,
               |            "additionalFields": [
               |                {
               |                    "name": "status",
               |                    "value": "Deployment failed"
               |                },
               |                {
               |                    "name": "created",
               |                    "value": "${activity4.createdAt}",
               |                    "isDate": true
               |                },
               |                {
               |                    "name": "started",
               |                    "value": "${toStringWith6DigitsOfMillis(activity4.date)}",
               |                    "isDate": true
               |                },
               |                {
               |                    "name": "finished",
               |                    "value": "${toStringWith6DigitsOfMillis(activity4.dateFinished)}",
               |                    "isDate": true
               |                }
               |            ],
               |            "type": "PERFORMED_SCHEDULED_EXECUTION"
               |        }
               |    ]
               |}
               |""".stripMargin
          )
        )
    }
    "PERFORMED_SCHEDULED_EXECUTION activity migrated by migration V1_070 should have adjusted date" in {
      val processId = createSavedScenario(exampleScenario)
      val now       = Instant.now

      // The createdAt and nextRetryAt fields in the migrated activity are moved by the time zone offset
      val timeZoneOffsetSeconds = ZoneId.systemDefault().getRules.getOffset(now).getTotalSeconds
      val nowWithOffset         = now.plusSeconds(timeZoneOffsetSeconds)
      val activity = ScenarioActivity.PerformedScheduledExecution(
        scenarioId = ScenarioId(processId.value),
        scenarioActivityId = ScenarioActivityId.random,
        user = ScenarioUser(None, UserName("Nussknacker"), None, None),
        date = now,
        scenarioVersionId = Some(ScenarioVersionId(1)),
        dateFinished = now,
        scheduleName = "schedule1",
        scheduledExecutionStatus = ScheduledExecutionStatus.DeploymentWillBeRetried,
        createdAt = nowWithOffset,
        retriesLeft = Some(2),
        nextRetryAt = Some(nowWithOffset.plusSeconds(1))
      )

      dbioValue(scenarioActivityRepository.addActivity(activity))

      // Mark the activity, as being imported by migration V1_070, by adding "imported_deployment_id" property
      dbioValue(
        scenarioActivityRepository.modifyActivityByActivityId[String](
          activityId = activity.scenarioActivityId,
          activityDoesNotExistError = "Activity does not exist",
          validateCurrentValue = _ => Right(()),
          modify = entity => {
            entity.copy(
              additionalProperties = entity.additionalProperties.withProperty("imported_deployment_id", "1")
            )
          },
          couldNotModifyError = "Could not modify activity"
        )
      )

      given()
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity/activities")
        .Then()
        .statusCode(200)
        .body(
          matchJsonWithRegexValues(
            s"""
               |{
               |    "activities": [
               |        {
               |            "id": "${regexes.looseUuidRegex}",
               |            "user": "admin",
               |            "date": "${regexes.zuluDateRegex}",
               |            "scenarioVersionId": 1,
               |            "additionalFields": [
               |
               |            ],
               |            "type": "SCENARIO_CREATED"
               |        },
               |        {
               |            "id": "${regexes.looseUuidRegex}",
               |            "user": "Nussknacker",
               |            "date": "${regexes.zuluDateRegex}",
               |            "scenarioVersionId": 1,
               |            "additionalFields": [
               |                {
               |                    "name": "status",
               |                    "value": "Deployment will be retried"
               |                },
               |                {
               |                    "name": "created",
               |                    "value": "${now}",
               |                    "isDate": true
               |                },
               |                {
               |                    "name": "started",
               |                    "value": "${toStringWith6DigitsOfMillis(now)}",
               |                    "isDate": true
               |                },
               |                {
               |                    "name": "finished",
               |                    "value": "${toStringWith6DigitsOfMillis(now)}",
               |                    "isDate": true
               |                },
               |                {
               |                    "name": "retriesLeft",
               |                    "value": "2"
               |                },
               |                {
               |                    "name": "nextRetryAt",
               |                    "value": "${now.plusSeconds(1)}",
               |                    "isDate": true
               |                }
               |            ],
               |            "type": "PERFORMED_SCHEDULED_EXECUTION"
               |        }
               |    ]
               |}
               |""".stripMargin
          )
        )
    }
    "return 404 for no existing scenario" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/processes/$wrongScenarioName/activity/activities")
        .Then()
        .statusCode(404)
        .equalsPlainBody(s"No scenario $wrongScenarioName found")
    }
  }

  "The scenario add comment endpoint when" - {
    "add comment in existing scenario" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .plainBody(commentContent)
        .post(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/1/activity/comment")
        .Then()
        .statusCode(200)
        .verifyApplicationState {
          verifyCommentExists(
            scenarioName = exampleScenarioName,
            commentContent = commentContent,
            commentUser = "allpermuser"
          )
        }
    }
    "return 404 for no existing scenario" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .plainBody(commentContent)
        .post(s"$nuDesignerHttpAddress/api/processes/$wrongScenarioName/1/activity/comment")
        .Then()
        .statusCode(404)
        .equalsPlainBody(s"No scenario $wrongScenarioName found")
    }
  }

  "The scenario edit comment endpoint when" - {
    "edit comment in existing scenario" in {
      val newContent = "New comment content after modification"

      val commentActivityId = given()
        .applicationState {
          createSavedScenario(exampleScenario)
          createComment(scenarioName = exampleScenarioName, commentContent = commentContent)
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity/activities")
        .Then()
        .extractString("activities[1].id") // First activity (0) is SCENARIO_CREATED, second (1) is COMMENT_ADDED

      given()
        .when()
        .basicAuthAllPermUser()
        .plainBody(newContent)
        .put(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity/comment/$commentActivityId")
        .Then()
        .statusCode(200)
        .verifyApplicationState {
          verifyCommentExists(
            scenarioName = exampleScenarioName,
            commentContent = newContent,
            commentUser = "allpermuser"
          )
        }
    }
    "return 200 when editing deployment comment with valid format" in {
      val newContent = "[JIRA-12345] Very important feature"

      val scenarioDeployedActivityId = given()
        .applicationState {
          createDeployedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity/activities")
        .Then()
        .extractString("activities[1].id")

      given()
        .when()
        .basicAuthAllPermUser()
        .plainBody(newContent)
        .put(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity/comment/$scenarioDeployedActivityId")
        .Then()
        .statusCode(200)
        .verifyApplicationState {
          verifyCommentExists(
            scenarioName = exampleScenarioName,
            commentContent = s"Deployment: $newContent",
            commentUser = "admin"
          )
        }
    }
    "return 400 when editing deployment comment with invalid format" in {
      val newContent = "New comment content after modification"

      val scenarioDeployedActivityId = given()
        .applicationState {
          createDeployedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity/activities")
        .Then()
        .extractString("activities[1].id")

      given()
        .when()
        .basicAuthAllPermUser()
        .plainBody(newContent)
        .put(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity/comment/$scenarioDeployedActivityId")
        .Then()
        .statusCode(400)
        .equalsPlainBody(
          "Bad comment format 'New comment content after modification'. Example comment: [JIRA-1234] description."
        )
    }
    "return 404 for no existing scenario" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
          createComment(scenarioName = exampleScenarioName, commentContent = commentContent)
        }
        .when()
        .basicAuthAllPermUser()
        .plainBody(commentContent)
        .post(s"$nuDesignerHttpAddress/api/processes/$wrongScenarioName/1/activity/comment")
        .Then()
        .statusCode(404)
        .equalsPlainBody(s"No scenario $wrongScenarioName found")
    }
  }

  "The scenario remove comment endpoint when" - {
    "remove comment in existing scenario" in {
      val commentActivityId = given()
        .applicationState {
          createSavedScenario(exampleScenario)
          createComment(scenarioName = exampleScenarioName, commentContent = commentContent)
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity/activities")
        .Then()
        .extractString("activities[1].id") // First activity (0) is SCENARIO_CREATED, second (1) is COMMENT_ADDED

      given()
        .when()
        .basicAuthAllPermUser()
        .delete(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity/comment/$commentActivityId")
        .Then()
        .statusCode(200)
        .verifyApplicationState {
          verifyEmptyCommentsAndAttachments(exampleScenarioName)
        }
    }
    "return 400 when removing deployment comment with required non-empty content" in {
      val commentActivityId = given()
        .applicationState {
          createDeployedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity/activities")
        .Then()
        .extractString("activities[1].id") // First activity (0) is SCENARIO_CREATED, second (1) is SCENARIO_DEPLOYED

      given()
        .when()
        .basicAuthAllPermUser()
        .delete(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity/comment/$commentActivityId")
        .Then()
        .statusCode(400)
        .equalsPlainBody("Comment is required.")
    }
    "return 500 for no existing comment" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .delete(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity/comments/1")
        .Then()
        .statusCode(500)
        .equalsPlainBody("Unable to delete comment with id: 1")
    }
    "return 404 for no existing scenario" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .delete(s"$nuDesignerHttpAddress/api/processes/$wrongScenarioName/activity/comments/1")
        .Then()
        .statusCode(404)
        .equalsPlainBody(s"No scenario $wrongScenarioName found")
    }
  }

  private def createComment(scenarioName: String, commentContent: String): Unit = {
    given()
      .when()
      .plainBody(commentContent)
      .basicAuthAllPermUser()
      .when()
      .post(s"$nuDesignerHttpAddress/api/processes/$scenarioName/1/activity/comment")
  }

  private def createAttachment(
      scenarioName: String,
      fileContent: String,
      fileName: String = "important_file.txt"
  ): Unit = {
    given()
      .when()
      .basicAuthAllPermUser()
      .streamBody(fileContent = fileContent, fileName = fileName)
      .post(s"$nuDesignerHttpAddress/api/processes/$scenarioName/1/activity/attachments")
  }

  private def dbioValue[T](action: DB[T]): T = {
    dbioRunner.run(action).futureValue
  }

  private def toStringWith6DigitsOfMillis(instant: Instant) = {
    val formatter = new DateTimeFormatterBuilder()
      .appendInstant(6)
      .toFormatter()
    formatter.format(instant)
  }

}
