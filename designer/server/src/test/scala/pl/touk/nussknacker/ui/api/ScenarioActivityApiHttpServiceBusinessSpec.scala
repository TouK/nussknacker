package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import io.restassured.response.ValidatableResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.test.{NuRestAssureExtensions, NuRestAssureMatchers, RestAssuredVerboseLogging}
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{
  WithMockableDeploymentManager,
  WithSimplifiedConfigRestAssuredUsersExtensions,
  WithSimplifiedDesignerConfig
}

import java.util.UUID

class ScenarioActivityApiHttpServiceBusinessSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithSimplifiedConfigScenarioHelper
    with WithMockableDeploymentManager
    with WithSimplifiedConfigRestAssuredUsersExtensions
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging {

  import ScenarioActivitySpecAsserts._

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

  "The scenario activity endpoint when" - {
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

  "The scenario add comment endpoint when" - {
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
        .verifyCommentExists(scenarioName = exampleScenarioName, commentContent = commentContent)
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

  "The scenario remove comment endpoint when" - {
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
        .verifyEmptyCommentsAndAttachments(exampleScenarioName)
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
        .verifyAttachmentsExists(exampleScenarioName)
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

  private def createComment(scenarioName: String, commentContent: String): Unit = {
    given()
      .when()
      .plainBody(commentContent)
      .basicAuthAllPermUser()
      .when()
      .post(s"$nuDesignerHttpAddress/api/processes/$scenarioName/1/activity/comments")
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

  object ScenarioActivitySpecAsserts {

    implicit class VerifyCommentExists[T <: ValidatableResponse](validatableResponse: T) {

      def verifyCommentExists(scenarioName: String, commentContent: String): ValidatableResponse = {
        given()
          .when()
          .basicAuthAllPermUser()
          .get(s"$nuDesignerHttpAddress/api/processes/$scenarioName/activity")
          .Then()
          .statusCode(200)
          .body(
            matchJsonWithRegexValues(
              s"""
                 |{
                 |  "comments": [
                 |    {
                 |      "id": "${regexes.digitsRegex}",
                 |      "processVersionId": 1,
                 |      "content": "$commentContent",
                 |      "user": "allpermuser",
                 |      "createDate": "${regexes.zuluDateRegex}"
                 |    }
                 |  ],
                 |  "attachments": []
                 |}
                 |""".stripMargin
            )
          )
      }

    }

    implicit class VerifyEmptyCommentsAndAttachments[T <: ValidatableResponse](validatableResponse: T) {

      def verifyEmptyCommentsAndAttachments(scenarioName: String): ValidatableResponse = {
        given()
          .when()
          .basicAuthAllPermUser()
          .get(s"$nuDesignerHttpAddress/api/processes/$scenarioName/activity")
          .Then()
          .equalsJsonBody(
            s"""
               |{
               |  "comments": [],
               |  "attachments": []
               |}
               |""".stripMargin
          )
      }

    }

    implicit class VerifyAttachmentsExists[T <: ValidatableResponse](validatableResponse: T) {

      def verifyAttachmentsExists(scenarioName: String): ValidatableResponse = {
        given()
          .when()
          .basicAuthAllPermUser()
          .get(s"$nuDesignerHttpAddress/api/processes/$scenarioName/activity")
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
                 |    }
                 |  ]
                 |}
                 |""".stripMargin
            )
          )
      }

    }

  }

}
