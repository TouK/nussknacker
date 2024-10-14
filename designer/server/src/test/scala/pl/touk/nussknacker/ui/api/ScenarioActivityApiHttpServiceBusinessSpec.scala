package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.development.manager.MockableDeploymentManagerProvider.MockableDeploymentManager
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{
  WithBusinessCaseRestAssuredUsersExtensions,
  WithMockableDeploymentManager,
  WithSimplifiedDesignerConfig
}
import pl.touk.nussknacker.test.processes.WithScenarioActivitySpecAsserts
import pl.touk.nussknacker.test.{
  NuRestAssureExtensions,
  NuRestAssureMatchers,
  RestAssuredVerboseLoggingIfValidationFails
}

import java.time.Instant
import java.util.UUID

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
    with RestAssuredVerboseLoggingIfValidationFails {

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

    "return SCENARIO_CREATED activity and activities returned by deployment manager, without failed non-batch activity" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
          MockableDeploymentManager.configureManagerSpecificScenarioActivities(
            List(
              ScenarioActivity.CustomAction(
                scenarioId = ScenarioId(123),
                scenarioActivityId = ScenarioActivityId.random,
                user = ScenarioUser(None, UserName("custom-user"), None, None),
                date = clock.instant(),
                scenarioVersionId = None,
                dateFinished = Some(Instant.now()),
                actionName = "Custom action handled by deployment manager",
                comment = ScenarioComment.Available(
                  comment = "Executed on custom deployment manager",
                  lastModifiedByUserName = UserName("custom-user"),
                  lastModifiedAt = clock.instant()
                ),
                result = DeploymentRelatedActivityResult.Success
              ),
              ScenarioActivity.CustomAction(
                scenarioId = ScenarioId(123),
                scenarioActivityId = ScenarioActivityId.random,
                user = ScenarioUser(None, UserName("custom-user"), None, None),
                date = clock.instant(),
                scenarioVersionId = None,
                dateFinished = None,
                actionName = "Custom action handled by deployment manager",
                comment = ScenarioComment.Available(
                  comment = "Executed on custom deployment manager",
                  lastModifiedByUserName = UserName("custom-user"),
                  lastModifiedAt = clock.instant()
                ),
                result = DeploymentRelatedActivityResult.Failure(None)
              )
            )
          )
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
               |     {
               |      "id": "${regexes.looseUuidRegex}",
               |      "user": "admin",
               |      "date": "${regexes.zuluDateRegex}",
               |      "scenarioVersionId": 1,
               |      "additionalFields": [],
               |      "type": "SCENARIO_CREATED"
               |     },
               |     {
               |       "id": "${regexes.looseUuidRegex}",
               |       "user": "custom-user",
               |       "date": "${regexes.zuluDateRegex}",
               |       "comment": {
               |         "content": {
               |           "value": "Executed on custom deployment manager",
               |           "status": "AVAILABLE"
               |         },
               |         "lastModifiedBy": "custom-user",
               |         "lastModifiedAt": "${regexes.zuluDateRegex}"
               |       },
               |       "additionalFields": [
               |         {"name": "actionName", "value": "Custom action handled by deployment manager"}
               |       ],
               |       "type": "CUSTOM_ACTION"
               |     }
               |  ]
               |}
               |""".stripMargin
          )
        )
    }

    "return SCENARIO_CREATED activity and activities returned by deployment manager, with failed batch activity" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
          MockableDeploymentManager.configureManagerSpecificScenarioActivities(
            List(
              ScenarioActivity.PerformedSingleExecution(
                scenarioId = ScenarioId(123),
                scenarioActivityId = ScenarioActivityId.random,
                user = ScenarioUser(None, UserName("custom-user"), None, None),
                date = clock.instant(),
                scenarioVersionId = None,
                dateFinished = None,
                comment = ScenarioComment.Available(
                  comment = "Immediate execution",
                  lastModifiedByUserName = UserName("custom-user"),
                  lastModifiedAt = clock.instant()
                ),
                result = DeploymentRelatedActivityResult.Success
              )
            )
          )
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
               |     {
               |      "id": "${regexes.looseUuidRegex}",
               |      "user": "admin",
               |      "date": "${regexes.zuluDateRegex}",
               |      "scenarioVersionId": 1,
               |      "additionalFields": [],
               |      "type": "SCENARIO_CREATED"
               |     },
               |     {
               |       "id": "${regexes.looseUuidRegex}",
               |       "user": "custom-user",
               |       "date": "${regexes.zuluDateRegex}",
               |       "comment": {
               |         "content": {
               |           "value": "Immediate execution",
               |           "status": "AVAILABLE"
               |         },
               |         "lastModifiedBy": "custom-user",
               |         "lastModifiedAt": "${regexes.zuluDateRegex}"
               |       },
               |       "additionalFields": [],
               |       "type": "PERFORMED_SINGLE_EXECUTION"
               |     }
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

}
