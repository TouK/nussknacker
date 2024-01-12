package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.`given`
import io.restassured.http.ContentType
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import io.restassured.response.{Response, ValidatableResponse}
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.test.{NuRestAssureExtensions, NuRestAssureMatchers, RestAssuredVerboseLogging}
import pl.touk.nussknacker.ui.api.helpers.TestCategories.Category1
import pl.touk.nussknacker.ui.api.helpers.{
  NuItTest,
  NuTestScenarioManager,
  TestProcessingTypes,
  WithMockableDeploymentManager
}

import java.nio.charset.StandardCharsets
import java.util.UUID

class ScenarioActivitySpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithMockableDeploymentManager
    with NuTestScenarioManager
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging {
  import ScenarioActivitySpecAsserts._

  val scenarioName      = ProcessName(UUID.randomUUID().toString)
  val commentContent    = "test message"
  val wrongScenarioName = ProcessName("wrongProcessName")
  val fileContent       = "very important content"
  val fileName          = "important_file.txt"

  val scenario: CanonicalProcess = ScenarioBuilder
    .streaming(scenarioName.value)
    .source("sourceId", "barSource")
    .emptySink("sinkId", "barSink")

  "The scenario activity endpoint when" - {
    "authenticated should" - {
      "return empty comments and attachment for existing process without them" in {
        given()
          .applicationState {
            createSavedScenario(scenario, category = Category1, TestProcessingTypes.Streaming)
          }
          .basicAuth("reader", "reader")
          .when()
          .get(s"$nuDesignerHttpAddress/api/processes/${scenarioName.value}/activity")
          .Then()
          .statusCode(200)
          .equalsJsonBody(
            s"""
               |{
               |    "comments": [],
               |    "attachments": []
               |}
               |""".stripMargin
          )
      }

      "return 404 for no existing scenario" in {
        given()
          .basicAuth("reader", "reader")
          .when()
          .get(s"$nuDesignerHttpAddress/api/processes/${wrongScenarioName.value}/activity")
          .Then()
          .statusCode(404)
          .equalsPlainBody(s"No scenario $wrongScenarioName found")
      }
    }

    "not authenticated should" - {
      "forbid access" in {
        given()
          .contentType(ContentType.JSON.withCharset(StandardCharsets.UTF_8))
          .noAuth()
          .when()
          .get(s"$nuDesignerHttpAddress/api/processes/${scenarioName.value}/activity")
          .Then()
          .statusCode(401)
          .equalsPlainBody("The resource requires authentication, which was not supplied with the request")
      }
    }
  }

  "The scenario add comment endpoint when" - {
    "authenticated should" - {
      "add comment in existing scenario" in {
        given()
          .applicationState {
            createSavedScenario(scenario, category = Category1, TestProcessingTypes.Streaming)
          }
          .plainBody(commentContent)
          .basicAuth("writer", "writer")
          .when()
          .post(s"$nuDesignerHttpAddress/api/processes/${scenarioName.value}/1/activity/comments")
          .Then()
          .statusCode(200)
          .verifyCommentExists(scenarioName, commentContent)
      }

      "return 404 for no existing scenario" in {
        given()
          .applicationState {
            createSavedScenario(scenario, category = Category1, TestProcessingTypes.Streaming)
          }
          .plainBody(commentContent)
          .basicAuth("writer", "writer")
          .when()
          .post(s"$nuDesignerHttpAddress/api/processes/${wrongScenarioName.value}/1/activity/comments")
          .Then()
          .statusCode(404)
          .equalsPlainBody(s"No scenario $wrongScenarioName found")
      }
    }

    "not authenticated should" - {
      "forbid access for no authorization" in {
        given()
          .plainBody(commentContent)
          .noAuth()
          .when()
          .post(s"$nuDesignerHttpAddress/api/processes/${scenarioName.value}/1/activity/comments")
          .Then()
          .statusCode(401)
          .equalsPlainBody("The resource requires authentication, which was not supplied with the request")
      }

      "forbid access for insufficient privileges" in {
        given()
          .applicationState {
            createSavedScenario(scenario, category = Category1, TestProcessingTypes.Streaming)
          }
          .plainBody(commentContent)
          .basicAuth("reader", "reader")
          .when()
          .post(s"$nuDesignerHttpAddress/api/processes/${scenarioName.value}/1/activity/comments")
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
            createSavedScenario(scenario, category = Category1, TestProcessingTypes.Streaming)
            createComment(scenarioName, commentContent)
          }
          .basicAuth("reader", "reader")
          .when()
          .get(s"$nuDesignerHttpAddress/api/processes/${scenarioName.value}/activity")
          .Then()
          .extractLong("comments[0].id")

        given()
          .basicAuth("writer", "writer")
          .when()
          .delete(s"$nuDesignerHttpAddress/api/processes/${scenarioName.value}/activity/comments/$commentId")
          .Then()
          .statusCode(200)
          .verifyEmptyCommentsAndAttachments(scenarioName)
      }

      "return 500 for no existing comment" in {
        given()
          .applicationState {
            createSavedScenario(scenario, category = Category1, TestProcessingTypes.Streaming)
          }
          .basicAuth("writer", "writer")
          .when()
          .delete(s"$nuDesignerHttpAddress/api/processes/${scenarioName.value}/activity/comments/1")
          .Then()
          .statusCode(500)
          .equalsPlainBody("Unable to delete comment with id: 1")
      }

      "return 404 for no existing scenario" in {
        given()
          .basicAuth("writer", "writer")
          .when()
          .delete(s"$nuDesignerHttpAddress/api/processes/${wrongScenarioName.value}/activity/comments/1")
          .Then()
          .statusCode(404)
          .equalsPlainBody(s"No scenario $wrongScenarioName found")
      }
    }

    "not authenticated should" - {
      "forbid access for no authorization" in {
        given()
          .noAuth()
          .when()
          .delete(s"$nuDesignerHttpAddress/api/processes/${scenario.name}/activity/comments/1")
          .Then()
          .statusCode(401)
          .equalsPlainBody("The resource requires authentication, which was not supplied with the request")
      }

      "forbid access for insufficient privileges" in {
        given()
          .applicationState {
            createSavedScenario(scenario, category = Category1, TestProcessingTypes.Streaming)
          }
          .basicAuth("reader", "reader")
          .when()
          .delete(s"$nuDesignerHttpAddress/api/processes/${scenario.name}/activity/comments/1")
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
            createSavedScenario(scenario, category = Category1, TestProcessingTypes.Streaming)
          }
          .multiPartBody(fileContent = fileContent, fileName = fileName)
          .basicAuth("writer", "writer")
          .when()
          .post(s"$nuDesignerHttpAddress/api/processes/${scenarioName.value}/1/activity/attachments")
          .Then()
          .statusCode(200)
          .verifyAttachmentsExists(scenarioName)
      }

      "handle attachments with the same name" in {
        val fileContent1 = "very important content1"
        val fileContent2 = "very important content2"
        given()
          .applicationState {
            createSavedScenario(scenario, category = Category1, TestProcessingTypes.Streaming)
            createAttachment(scenarioName, fileContent = fileContent1, fileName = fileName)
            createAttachment(scenarioName, fileContent = fileContent2, fileName = fileName)
          }
          .basicAuth("reader", "reader")
          .when()
          .get(s"$nuDesignerHttpAddress/api/processes/${scenarioName.value}/activity")
          .Then()
          .body(
            matchJsonWithRegexValues(
              s"""
                 |{
                 |    "comments": [],
                 |    "attachments": [
                 |        {
                 |            "id": "${regexes.digitsRegex}",
                 |            "processVersionId": 1,
                 |            "fileName": "important_file.txt",
                 |            "user": "writer",
                 |            "createDate": "${regexes.zuluDateRegex}"
                 |        },
                 |        {
                 |            "id": "${regexes.digitsRegex}",
                 |            "processVersionId": 1,
                 |            "fileName": "important_file.txt",
                 |            "user": "writer",
                 |            "createDate": "${regexes.zuluDateRegex}"
                 |        }
                 |    ]
                 |}
                 |""".stripMargin
            )
          )
      }

      "return 404 for no existing scenario" in {
        given()
          .multiPartBody(fileContent = fileContent, fileName = fileName)
          .basicAuth("writer", "writer")
          .when()
          .post(s"$nuDesignerHttpAddress/api/processes/${wrongScenarioName.value}/1/activity/attachments")
          .Then()
          .statusCode(404)
          .equalsPlainBody(s"No scenario $wrongScenarioName found")
      }
    }

    "not authenticated should" - {
      "forbid access for no authorization" in {
        given()
          .multiPartBody(fileContent = "test", fileName = "test.xml")
          .noAuth()
          .when()
          .post(s"$nuDesignerHttpAddress/api/processes/${scenarioName.value}/1/activity/attachments")
          .Then()
          .statusCode(401)
          .equalsPlainBody("The resource requires authentication, which was not supplied with the request")
      }

      "forbid access for insufficient privileges" in {
        given()
          .applicationState {
            createSavedScenario(scenario, category = Category1, TestProcessingTypes.Streaming)
          }
          .multiPartBody(fileContent = "test", fileName = "test.xml")
          .basicAuth("reader", "reader")
          .when()
          .post(s"$nuDesignerHttpAddress/api/processes/${scenarioName.value}/1/activity/attachments")
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
            createSavedScenario(scenario, category = Category1, TestProcessingTypes.Streaming)
            createAttachment(scenarioName, fileContent)
          }
          .basicAuth("reader", "reader")
          .when()
          .get(s"$nuDesignerHttpAddress/api/processes/${scenarioName.value}/activity")
          .Then()
          .extractLong("attachments[0].id")

        given()
          .basicAuth("writer", "writer")
          .when()
          .get(s"$nuDesignerHttpAddress/api/processes/${scenarioName.value}/activity/attachments/$attachmentId")
          .Then()
          .statusCode(200)
          .equalsPlainBody(fileContent)
      }

      "return empty body for no existing attachment" in {
        given()
          .applicationState {
            createSavedScenario(scenario, category = Category1, TestProcessingTypes.Streaming)
          }
          .basicAuth("writer", "writer")
          .when()
          .get(s"$nuDesignerHttpAddress/api/processes/${scenarioName.value}/activity/attachments/1")
          .Then()
          .statusCode(200)
          .equalsPlainBody("")
      }

      "return 404 for no existing scenario" in {
        given()
          .basicAuth("writer", "writer")
          .when()
          .get(s"$nuDesignerHttpAddress/api/processes/${wrongScenarioName.value}/activity/attachments/1")
          .Then()
          .statusCode(404)
          .equalsPlainBody(s"No scenario $wrongScenarioName found")
      }
    }

    "not authenticated should" - {
      "forbid access for no authorization" in {
        given()
          .noAuth()
          .when()
          .get(s"$nuDesignerHttpAddress/api/processes/${scenarioName.value}/activity/attachments/1")
          .Then()
          .statusCode(401)
          .equalsPlainBody("The resource requires authentication, which was not supplied with the request")
      }
    }
  }

  private def createComment(scenarioName: ProcessName, commentContent: String): ValidatableResponse = {
    given()
      .plainBody(commentContent)
      .basicAuth("writer", "writer")
      .when()
      .post(s"$nuDesignerHttpAddress/api/processes/${scenarioName.value}/1/activity/comments")
      .Then()
  }

  private def createAttachment(
      scenarioName: ProcessName,
      fileContent: String,
      fileName: String = "important_file.txt"
  ): Response = {
    given()
      .multiPartBody(fileContent = fileContent, fileName = fileName)
      .basicAuth("writer", "writer")
      .when()
      .post(s"$nuDesignerHttpAddress/api/processes/${scenarioName.value}/1/activity/attachments")
  }

  object ScenarioActivitySpecAsserts {

    implicit class VerifyCommentExists[T <: ValidatableResponse](validatableResponse: T) {

      def verifyCommentExists(scenarioName: ProcessName, commentContent: String): ValidatableResponse = {
        given()
          .basicAuth("reader", "reader")
          .when()
          .get(s"$nuDesignerHttpAddress/api/processes/${scenarioName.value}/activity")
          .Then()
          .body(
            matchJsonWithRegexValues(
              s"""
                 |{
                 |    "comments": [
                 |        {
                 |            "id": "${regexes.digitsRegex}",
                 |            "processVersionId": 1,
                 |            "content": "$commentContent",
                 |            "user": "writer",
                 |            "createDate": "${regexes.zuluDateRegex}"
                 |        }
                 |    ],
                 |    "attachments": []
                 |}
                 |""".stripMargin
            )
          )
      }

    }

    implicit class VerifyEmptyCommentsAndAttachments[T <: ValidatableResponse](validatableResponse: T) {

      def verifyEmptyCommentsAndAttachments(scenarioName: ProcessName): ValidatableResponse = {
        given()
          .basicAuth("reader", "reader")
          .when()
          .get(s"$nuDesignerHttpAddress/api/processes/${scenarioName.value}/activity")
          .Then()
          .equalsJsonBody(
            s"""
               |{
               |    "comments": [],
               |    "attachments": []
               |}
               |""".stripMargin
          )
      }

    }

    implicit class VerifyAttachmentsExists[T <: ValidatableResponse](validatableResponse: T) {

      def verifyAttachmentsExists(scenarioName: ProcessName): ValidatableResponse = {
        given()
          .basicAuth("reader", "reader")
          .when()
          .get(s"$nuDesignerHttpAddress/api/processes/${scenarioName.value}/activity")
          .Then()
          .body(
            matchJsonWithRegexValues(
              s"""
                 |{
                 |    "comments": [],
                 |    "attachments": [
                 |        {
                 |            "id": "${regexes.digitsRegex}",
                 |            "processVersionId": 1,
                 |            "fileName": "important_file.txt",
                 |            "user": "writer",
                 |            "createDate": "${regexes.zuluDateRegex}"
                 |        }
                 |    ]
                 |}
                 |""".stripMargin
            )
          )
      }

    }

  }

}
