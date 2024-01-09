package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.`given`
import io.restassured.builder.MultiPartSpecBuilder
import io.restassured.http.ContentType
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import io.restassured.response.ValidatableResponse
import org.hamcrest.core.IsEqual
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.test.{NuRestAssureExtensions, NuRestAssureMatchers, RestAssuredVerboseLogging}
import pl.touk.nussknacker.ui.api.helpers.TestCategories.Category1
import pl.touk.nussknacker.ui.api.helpers.{
  NuItTest,
  NuScenarioConfigurationHelper,
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
    "add and remove comment in process activity" in {
      given()
        .applicationConfiguration {
          createSavedProcess(process, category = Category1, TestProcessingTypes.Streaming)
        }
        .contentType(ContentType.TEXT.withCharset(StandardCharsets.UTF_8))
        .body(commentContent)
        .basicAuth("writer", "writer")
        .when()
        .post(s"$nuDesignerHttpAddress/api/processes/${processName.value}/1/activity/comments")
        .Then()
        .statusCode(200)

      val commentId = getActivity(processName)
        .body(
          matchJsonWithRegexValues(
            s"""
               |{
               |    "comments": [
               |        {
               |            "id": "$digitsRegex",
               |            "processVersionId": 1,
               |            "content": "$commentContent",
               |            "user": "writer",
               |            "createDate": "$zuluDateRegex"
               |        }
               |    ],
               |    "attachments": []
               |}
               |""".stripMargin
          )
        )
        .extract()
        .jsonPath()
        .getLong("comments[0].id")

      given()
        .basicAuth("writer", "writer")
        .when()
        .delete(s"$nuDesignerHttpAddress/api/processes/${process.name}/activity/comments/$commentId")
        .Then()
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

    "add attachment to process and then be able to download it" in {
      val fileContent = "very important content"
      createSavedProcess(process, category = Category1, TestProcessingTypes.Streaming)
      createAttachment(processName, fileContent)

      val attachmentId = getActivity(processName)
        .body(
          matchJsonWithRegexValues(s"""
               |{
               |    "comments": [],
               |    "attachments": [
               |        {
               |            "id": "$digitsRegex",
               |            "processVersionId": 1,
               |            "fileName": "important_file.txt",
               |            "user": "writer",
               |            "createDate": "$zuluDateRegex"
               |        }
               |    ]
               |}
               |""".stripMargin)
        )
        .extract()
        .jsonPath()
        .getLong("attachments[0].id")

      given()
        .basicAuth("writer", "writer")
        .when()
        .get(s"$nuDesignerHttpAddress/api/processes/${processName.value}/activity/attachments/$attachmentId")
        .Then()
        .statusCode(200)
        .body(new IsEqual(fileContent))
    }

    "handle attachments with the same name" in {
      val fileName     = "important_file.txt"
      val fileContent1 = "very important content1"
      val fileContent2 = "very important content2"
      createSavedProcess(process, category = Category1, TestProcessingTypes.Streaming)
      createAttachment(processName, fileContent = fileContent1, fileName = fileName)
      createAttachment(processName, fileContent = fileContent2, fileName = fileName)

      getActivity(processName)
        .body(
          matchJsonWithRegexValues(s"""
               |{
               |    "comments": [],
               |    "attachments": [
               |        {
               |            "id": "$digitsRegex",
               |            "processVersionId": 1,
               |            "fileName": "important_file.txt",
               |            "user": "writer",
               |            "createDate": "$zuluDateRegex"
               |        },
               |        {
               |            "id": "$digitsRegex",
               |            "processVersionId": 1,
               |            "fileName": "important_file.txt",
               |            "user": "writer",
               |            "createDate": "$zuluDateRegex"
               |        }
               |    ]
               |}
               |""".stripMargin)
        )
    }
  }

  private def getActivity(processName: ProcessName): ValidatableResponse = {
    given()
      .contentType(ContentType.JSON.withCharset(StandardCharsets.UTF_8))
      .basicAuth("reader", "reader")
      .when()
      .get(s"$nuDesignerHttpAddress/api/processes/${processName.value}/activity")
      .Then()
      .statusCode(200)
  }

  private def createAttachment(
      processName: ProcessName,
      fileContent: String,
      fileName: String = "important_file.txt"
  ): ValidatableResponse = {
    given()
      .multiPart(
        new MultiPartSpecBuilder(fileContent)
          // https://github.com/rest-assured/rest-assured/issues/866#issuecomment-617127889
          .header("Content-Disposition", s"form-data; name=\"attachment\"; filename=\"$fileName\"")
          .build()
      )
      .contentType(ContentType.MULTIPART)
      .basicAuth("writer", "writer")
      .when()
      .post(s"$nuDesignerHttpAddress/api/processes/${processName.value}/1/activity/attachments")
      .Then()
      .statusCode(200)
  }

}
