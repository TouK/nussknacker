package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import io.restassured.response.ValidatableResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.test.{NuRestAssureExtensions, NuRestAssureMatchers, RestAssuredVerboseLogging}
import pl.touk.nussknacker.ui.api.helpers.TestData.Categories.TestCategory.Category1
import pl.touk.nussknacker.ui.api.helpers.{NuItTest, NuTestScenarioManager, WithMockableDeploymentManager}

import java.util.UUID

class ScenarioActivityApiSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithMockableDeploymentManager
    with NuTestScenarioManager
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
    "authenticated should" - {
      "return empty comments and attachment for existing process without them" in {
        given()
          .applicationState {
            createSavedScenario(exampleScenario, category = Category1)
          }
          .basicAuth("reader", "reader")
          .when()
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
          .basicAuth("reader", "reader")
          .when()
          .get(s"$nuDesignerHttpAddress/api/processes/$wrongScenarioName/activity")
          .Then()
          .statusCode(404)
          .equalsPlainBody(s"No scenario $wrongScenarioName found")
      }
    }

    "not authenticated should" - {
      "forbid access" in {
        given()
          .basicAuth("unknown-user", "wrong-password")
          .when()
          .get(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity")
          .Then()
          .statusCode(401)
          .equalsPlainBody("The supplied authentication is invalid")
      }

      "forbid access for insufficient privileges" in {
        given()
          .applicationState {
            createSavedScenario(exampleScenario)
          }
          .plainBody(commentContent)
          .basicAuth("limitedReader", "limitedReader")
          .when()
          .get(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity")
          .Then()
          .statusCode(403)
          .equalsPlainBody("The supplied authentication is not authorized to access this resource")
      }
    }
  }

  "The scenario add comment endpoint when" - {
    "authenticated should" - {
      "add comment in existing scenario" in {
        given()
          .applicationState {
            createSavedScenario(exampleScenario, category = Category1)
          }
          .plainBody(commentContent)
          .basicAuth("writer", "writer")
          .when()
          .post(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/1/activity/comments")
          .Then()
          .statusCode(200)
          .verifyCommentExists(scenarioName = exampleScenarioName, commentContent = commentContent)
      }

      "return 404 for no existing scenario" in {
        given()
          .applicationState {
            createSavedScenario(exampleScenario, category = Category1)
          }
          .plainBody(commentContent)
          .basicAuth("writer", "writer")
          .when()
          .post(s"$nuDesignerHttpAddress/api/processes/$wrongScenarioName/1/activity/comments")
          .Then()
          .statusCode(404)
          .equalsPlainBody(s"No scenario $wrongScenarioName found")
      }
    }

    "not authenticated should" - {
      "forbid access for no authorization" in {
        given()
          .plainBody(commentContent)
          .basicAuth("unknown-user", "wrong-password")
          .when()
          .post(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/1/activity/comments")
          .Then()
          .statusCode(401)
          .equalsPlainBody("The supplied authentication is invalid")
      }

      "forbid access for insufficient privileges" in {
        given()
          .applicationState {
            createSavedScenario(exampleScenario, category = Category1)
          }
          .plainBody(commentContent)
          .basicAuth("reader", "reader")
          .when()
          .post(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/1/activity/comments")
          .Then()
          .statusCode(403)
          .equalsPlainBody("The supplied authentication is not authorized to access this resource")
      }
    }
  }

  "The scenario remove comment endpoint when" - {
    "authenticated should" - {
      "remove comment in existing scenario" in {
        val commentId = given()
          .applicationState {
            createSavedScenario(exampleScenario, category = Category1)
            createComment(scenarioName = exampleScenarioName, commentContent = commentContent)
          }
          .basicAuth("reader", "reader")
          .when()
          .get(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity")
          .Then()
          .extractLong("comments[0].id")

        given()
          .basicAuth("writer", "writer")
          .when()
          .delete(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity/comments/$commentId")
          .Then()
          .statusCode(200)
          .verifyEmptyCommentsAndAttachments(exampleScenarioName)
      }

      "return 500 for no existing comment" in {
        given()
          .applicationState {
            createSavedScenario(exampleScenario, category = Category1)
          }
          .basicAuth("writer", "writer")
          .when()
          .delete(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity/comments/1")
          .Then()
          .statusCode(500)
          .equalsPlainBody("Unable to delete comment with id: 1")
      }

      "return 404 for no existing scenario" in {
        given()
          .basicAuth("writer", "writer")
          .when()
          .delete(s"$nuDesignerHttpAddress/api/processes/$wrongScenarioName/activity/comments/1")
          .Then()
          .statusCode(404)
          .equalsPlainBody(s"No scenario $wrongScenarioName found")
      }
    }

    "not authenticated should" - {
      "forbid access for no authorization" in {
        given()
          .basicAuth("unknown-user", "wrong-password")
          .when()
          .delete(s"$nuDesignerHttpAddress/api/processes/${exampleScenario.name}/activity/comments/1")
          .Then()
          .statusCode(401)
          .equalsPlainBody("The supplied authentication is invalid")
      }

      "forbid access for insufficient privileges" in {
        given()
          .applicationState {
            createSavedScenario(exampleScenario, category = Category1)
          }
          .basicAuth("reader", "reader")
          .when()
          .delete(s"$nuDesignerHttpAddress/api/processes/${exampleScenario.name}/activity/comments/1")
          .Then()
          .statusCode(403)
          .equalsPlainBody("The supplied authentication is not authorized to access this resource")
      }
    }
  }

  "The scenario add attachment endpoint when" - {
    "authenticated should" - {
      "add attachment to existing scenario" in {
        given()
          .applicationState {
            createSavedScenario(exampleScenario, category = Category1)
          }
          .streamBody(fileContent = fileContent, fileName = fileName)
          .preemptiveBasicAuth("writer", "writer")
          .when()
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
            createSavedScenario(exampleScenario, category = Category1)
            createAttachment(scenarioName = exampleScenarioName, fileContent = fileContent1, fileName = fileName)
            createAttachment(scenarioName = exampleScenarioName, fileContent = fileContent2, fileName = fileName)
          }
          .preemptiveBasicAuth("reader", "reader")
          .when()
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
                 |      "user": "writer",
                 |      "createDate": "${regexes.zuluDateRegex}"
                 |    },
                 |    {
                 |      "id": "${regexes.digitsRegex}",
                 |      "processVersionId": 1,
                 |      "fileName": "important_file.txt",
                 |      "user": "writer",
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
          .streamBody(fileContent = fileContent, fileName = fileName)
          .preemptiveBasicAuth("writer", "writer")
          .when()
          .post(s"$nuDesignerHttpAddress/api/processes/$wrongScenarioName/1/activity/attachments")
          .Then()
          .statusCode(404)
          .equalsPlainBody(s"No scenario $wrongScenarioName found")
      }
    }

    "not authenticated should" - {
      "forbid access for no authorization" in {
        given()
          .streamBody(fileContent = "test", fileName = "test.xml")
          .noAuth()
          .when()
          .post(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/1/activity/attachments")
          .Then()
          .statusCode(401)
          .equalsPlainBody("The resource requires authentication, which was not supplied with the request")
      }

      "forbid access for insufficient privileges" in {
        given()
          .applicationState {
            createSavedScenario(exampleScenario, category = Category1)
          }
          .streamBody(fileContent = "test", fileName = "test.xml")
          .preemptiveBasicAuth("reader", "reader")
          .when()
          .post(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/1/activity/attachments")
          .Then()
          .statusCode(403)
          .equalsPlainBody("The supplied authentication is not authorized to access this resource")
      }
    }
  }

  "The scenario download attachment endpoint when" - {
    "authenticated should" - {
      "download existing attachment" in {
        val attachmentId = given()
          .applicationState {
            createSavedScenario(exampleScenario, category = Category1)
            createAttachment(scenarioName = exampleScenarioName, fileContent = fileContent)
          }
          .basicAuth("reader", "reader")
          .when()
          .get(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity")
          .Then()
          .extractLong("attachments[0].id")

        given()
          .basicAuth("writer", "writer")
          .when()
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
          .basicAuth("reader", "reader")
          .when()
          .get(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity")
          .Then()
          .extractLong("attachments[0].id")

        given()
          .basicAuth("reader", "reader")
          .applicationState {
            createSavedScenario(otherExampleScenario)
          }
          .when()
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
            createSavedScenario(exampleScenario, category = Category1)
          }
          .basicAuth("writer", "writer")
          .when()
          .get(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity/attachments/1")
          .Then()
          .statusCode(200)
          .equalsPlainBody("")
      }

      "return 404 for no existing scenario" in {
        given()
          .basicAuth("writer", "writer")
          .when()
          .get(s"$nuDesignerHttpAddress/api/processes/$wrongScenarioName/activity/attachments/1")
          .Then()
          .statusCode(404)
          .equalsPlainBody(s"No scenario $wrongScenarioName found")
      }
    }

    "not authenticated should" - {
      "forbid access for no authorization" in {
        given()
          .basicAuth("unknown-user", "wrong-password")
          .when()
          .get(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity/attachments/1")
          .Then()
          .statusCode(401)
          .equalsPlainBody("The supplied authentication is invalid")
      }

      "forbid access for insufficient privileges" in {
        given()
          .applicationState {
            createSavedScenario(exampleScenario)
          }
          .plainBody(commentContent)
          .basicAuth("limitedReader", "limitedReader")
          .when()
          .get(s"$nuDesignerHttpAddress/api/processes/$exampleScenarioName/activity/attachments/1")
          .Then()
          .statusCode(403)
          .equalsPlainBody("The supplied authentication is not authorized to access this resource")
      }
    }
  }

  private def createComment(scenarioName: String, commentContent: String): Unit = {
    given()
      .plainBody(commentContent)
      .basicAuth("writer", "writer")
      .when()
      .post(s"$nuDesignerHttpAddress/api/processes/$scenarioName/1/activity/comments")
  }

  private def createAttachment(
      scenarioName: String,
      fileContent: String,
      fileName: String = "important_file.txt"
  ): Unit = {
    given()
      .streamBody(fileContent = fileContent, fileName = fileName)
      .preemptiveBasicAuth("writer", "writer")
      .when()
      .post(s"$nuDesignerHttpAddress/api/processes/$scenarioName/1/activity/attachments")
  }

  object ScenarioActivitySpecAsserts {

    implicit class VerifyCommentExists[T <: ValidatableResponse](validatableResponse: T) {

      def verifyCommentExists(scenarioName: String, commentContent: String): ValidatableResponse = {
        given()
          .basicAuth("reader", "reader")
          .when()
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
                 |      "user": "writer",
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
          .basicAuth("reader", "reader")
          .when()
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
          .basicAuth("reader", "reader")
          .when()
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
                 |      "user": "writer",
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
