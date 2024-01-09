package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.`given`
import io.restassured.http.ContentType
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import io.restassured.response.ValidatableResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.test.{NuRestAssureExtensions, NuRestAssureMatchers, RestAssuredVerboseLogging}
import pl.touk.nussknacker.ui.api.helpers.TestCategories.Category1
import pl.touk.nussknacker.ui.api.helpers.{
  NuItTest,
  NuScenarioConfigurationHelper,
  TestCategories,
  TestProcessingTypes,
  WithMockableDeploymentManager
}

import java.nio.charset.StandardCharsets
import java.util.UUID

class ProcessActivitySpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithMockableDeploymentManager
    with NuScenarioConfigurationHelper
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging {

  val processName    = ProcessName(UUID.randomUUID().toString)
  val commentContent = "test message"

  val process: CanonicalProcess = ScenarioBuilder
    .streaming(processName.value)
    .source("sourceId", "barSource")
    .emptySink("sinkId", "barSink")

  "The process activity endpoint should" - {
    "get process activity" - {
      "authenticated should" - {
        "return empty comments and attachment for existing process without them" in {
          createSavedProcess(process, category = Category1, TestProcessingTypes.Streaming)

          getActivity(processName)
            .statusCode(200)
            .body(
              equalsJson(s"""
                   |{
                   |    "comments": [],
                   |    "attachments": []
                   |}
                   |""".stripMargin)
            )
        }

        "return 404 for no existing process" in {
          val wrongName = ProcessName("wrongProcessName")

          getActivity(wrongName)
            .statusCode(404)
            .equalsPlainBody(s"No scenario $wrongName found")
        }
      }

      "not authenticated should" - {
        "forbid access" in {
          given()
            .contentType(ContentType.JSON.withCharset(StandardCharsets.UTF_8))
            .noAuth()
            .when()
            .get(s"$nuDesignerHttpAddress/api/processes/${processName.value}/activity")
            .Then()
            .statusCode(401)
            .equalsPlainBody("The resource requires authentication, which was not supplied with the request")
        }
      }
    }

    "add comment when" - {
      "authenticated should" - {
        "add comment in existing process" in {
          createSavedProcess(process, category = Category1, TestProcessingTypes.Streaming)

          createComment(processName, commentContent)
            .statusCode(200)

          getActivity(processName)
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

        "return 404 for no existing process" in {
          val wrongName = ProcessName("wrongProcessName")

          createComment(wrongName, commentContent)
            .statusCode(404)
            .equalsPlainBody(s"No scenario $wrongName found")
        }
      }

      "not authenticated should" - {
        "forbid access" in {
          given()
            .plainBody(commentContent)
            .noAuth()
            .when()
            .post(s"$nuDesignerHttpAddress/api/processes/${processName.value}/1/activity/comments")
            .Then()
            .statusCode(401)
            .equalsPlainBody("The resource requires authentication, which was not supplied with the request")
        }
      }
    }

    "remove comment when" - {
      "authenticated should" - {
        "remove comment in existing process" in {
          createSavedProcess(process, category = Category1, TestProcessingTypes.Streaming)
          createComment(processName, commentContent)
          val commentId = getActivity(processName).extractLong("comments[0].id")

          deleteComment(processName, commentId)
            .statusCode(200)

          getActivity(processName)
            .body(
              equalsJson(s"""
                            |{
                            |    "comments": [],
                            |    "attachments": []
                            |}
                            |""".stripMargin)
            )
        }

        "return 500 for no existing comment" in {
          createSavedProcess(process, category = Category1, TestProcessingTypes.Streaming)

          deleteComment(processName, 1)
            .statusCode(500)
            .equalsPlainBody("Unable to delete comment with id: 1")
        }

        "return 404 for no existing process" in {
          val wrongName = ProcessName("wrongProcessName")

          deleteComment(wrongName, 1)
            .statusCode(404)
            .equalsPlainBody(s"No scenario $wrongName found")
        }
      }

      "not authenticated should" - {
        "forbid access" in {
          given()
            .noAuth()
            .when()
            .delete(s"$nuDesignerHttpAddress/api/processes/${process.name}/activity/comments/1")
            .Then()
            .statusCode(401)
            .equalsPlainBody("The resource requires authentication, which was not supplied with the request")
        }
      }
    }

    "add attachment when" - {
      "authenticated should" - {
        "add attachment to existing process" in {
          val fileContent = "very important content"
          createSavedProcess(process, category = Category1, TestProcessingTypes.Streaming)

          createAttachment(processName, fileContent).statusCode(200)

          getActivity(processName)
            .body(
              matchJsonWithRegexValues(s"""
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
                                          |""".stripMargin)
            )
        }

        "handle attachments with the same name" in {
          val fileName     = "important_file.txt"
          val fileContent1 = "very important content1"
          val fileContent2 = "very important content2"
          createSavedProcess(process, category = Category1, TestProcessingTypes.Streaming)

          createAttachment(processName, fileContent = fileContent1, fileName = fileName).statusCode(200)
          createAttachment(processName, fileContent = fileContent2, fileName = fileName).statusCode(200)

          getActivity(processName)
            .body(
              matchJsonWithRegexValues(s"""
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
                   |""".stripMargin)
            )
        }

        "return 404 for no existing process" in {
          val wrongName = ProcessName("wrongProcessName")

          createAttachment(wrongName, "test content")
            .statusCode(404)
            .equalsPlainBody(s"No scenario $wrongName found")
        }
      }

      "not authenticated should" - {
        "forbid access" in {
          given()
            .multiPartBody(fileContent = "test", fileName = "test.xml")
            .noAuth()
            .when()
            .post(s"$nuDesignerHttpAddress/api/processes/${processName.value}/1/activity/attachments")
            .Then()
            .statusCode(401)
            .equalsPlainBody("The resource requires authentication, which was not supplied with the request")
        }
      }
    }

    "download attachment when" - {
      "authenticated should" - {
        "download existing attachment" in {
          val fileContent = "very important content"
          createSavedProcess(process, category = Category1, TestProcessingTypes.Streaming)
          createAttachment(processName, fileContent)
          val attachmentId = getActivity(processName).extractLong("attachments[0].id")

          getAttachment(processName, attachmentId)
            .statusCode(200)
            .equalsPlainBody(fileContent)
        }

        "return empty body for no existing attachment" in {
          createSavedProcess(process, category = Category1, TestProcessingTypes.Streaming)

          getAttachment(processName, 1L)
            .statusCode(200)
            .equalsPlainBody("")
        }

        "return 404 for no existing process" in {
          val wrongName = ProcessName("wrongProcessName")

          getAttachment(wrongName, 1)
            .statusCode(404)
            .equalsPlainBody(s"No scenario $wrongName found")
        }
      }

      "not authenticated should" - {
        "forbid access" in {
          given()
            .noAuth()
            .when()
            .get(s"$nuDesignerHttpAddress/api/processes/${processName.value}/activity/attachments/1")
            .Then()
            .statusCode(401)
            .equalsPlainBody("The resource requires authentication, which was not supplied with the request")
        }
      }
    }
  }

  private def createComment(processName: ProcessName, commentContent: String): ValidatableResponse = {
    given()
      .plainBody(commentContent)
      .basicAuth("writer", "writer")
      .when()
      .post(s"$nuDesignerHttpAddress/api/processes/${processName.value}/1/activity/comments")
      .Then()
  }

  private def deleteComment(processName: ProcessName, commentId: Long): ValidatableResponse = {
    given()
      .basicAuth("writer", "writer")
      .when()
      .delete(s"$nuDesignerHttpAddress/api/processes/${processName.value}/activity/comments/$commentId")
      .Then()
  }

  private def getActivity(processName: ProcessName): ValidatableResponse = {
    given()
      .contentType(ContentType.JSON.withCharset(StandardCharsets.UTF_8))
      .basicAuth("reader", "reader")
      .when()
      .get(s"$nuDesignerHttpAddress/api/processes/${processName.value}/activity")
      .Then()
  }

  private def createAttachment(
      processName: ProcessName,
      fileContent: String,
      fileName: String = "important_file.txt"
  ): ValidatableResponse = {
    given()
      .multiPartBody(fileContent = fileContent, fileName = fileName)
      .basicAuth("writer", "writer")
      .when()
      .post(s"$nuDesignerHttpAddress/api/processes/${processName.value}/1/activity/attachments")
      .Then()
  }

  private def getAttachment(processName: ProcessName, attachmentId: Long): ValidatableResponse = {
    given()
      .basicAuth("writer", "writer")
      .when()
      .get(s"$nuDesignerHttpAddress/api/processes/${processName.value}/activity/attachments/$attachmentId")
      .Then()
  }

}
