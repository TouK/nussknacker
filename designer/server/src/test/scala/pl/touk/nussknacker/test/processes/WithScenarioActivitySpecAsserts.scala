package pl.touk.nussknacker.test.processes

import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import io.restassured.response.ValidatableResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.test.NuRestAssureMatchers
import pl.touk.nussknacker.test.base.it.NuItTest
import pl.touk.nussknacker.test.config.{WithBusinessCaseRestAssuredUsersExtensions, WithDesignerConfig}
import pl.touk.nussknacker.test.processes.WithScenarioActivitySpecAsserts.ScenarioActivitiesResponseWrapperForAddedAttachment

trait WithScenarioActivitySpecAsserts
    extends AnyFreeSpecLike
    with NuItTest
    with WithDesignerConfig
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureMatchers {

  def verifyCommentExists(scenarioName: String, commentContent: String, commentUser: String): Unit = {
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
               |      "processVersionId": "${regexes.digitsRegex}",
               |      "content": "$commentContent",
               |      "user": "${commentUser}",
               |      "createDate": "${regexes.zuluDateRegex}"
               |    }
               |  ],
               |  "attachments": []
               |}
               |""".stripMargin
        )
      )
  }

  def verifyIncomingMigrationActivityExists(
      scenarioName: String,
      sourceEnvironment: String,
      sourceUser: String,
      targetEnvironment: String,
  ): Unit = {
    given()
      .when()
      .basicAuthAllPermUser()
      .get(s"$nuDesignerHttpAddress/api/processes/$scenarioName/activity/activities")
      .Then()
      .statusCode(200)
      .body(
        matchJsonWithRegexValues(
          s"""
             |{
             |  "activities": [
             |    {
             |      "id": "${regexes.looseUuidRegex}",
             |      "user": "allpermuser",
             |      "date": "${regexes.zuluDateRegex}",
             |      "scenarioVersionId": 1,
             |      "additionalFields": [],
             |      "type": "SCENARIO_CREATED"
             |    },
             |    {
             |      "id": "${regexes.looseUuidRegex}",
             |      "user": "allpermuser",
             |      "date": "${regexes.zuluDateRegex}",
             |      "scenarioVersionId": 2,
             |      "additionalFields": [
             |        {
             |          "name": "sourceEnvironment",
             |          "value": "$sourceEnvironment"
             |        },
             |        {
             |          "name": "sourceUser",
             |          "value": "$sourceUser"
             |        },
             |        {
             |          "name": "targetEnvironment",
             |          "value": "$targetEnvironment"
             |        }
             |      ],
             |      "type": "INCOMING_MIGRATION"
             |    }
             |  ]
             |}
             |""".stripMargin
        )
      )
  }

  def verifyAttachmentAddedActivityExists(
      user: String,
      scenarioName: String,
      fileIdPresent: Boolean,
      filename: String,
      fileStatus: String,
      overrideDisplayableName: String,
  ): ScenarioActivitiesResponseWrapperForAddedAttachment = {
    val fileJson = if (fileIdPresent) {
      s"""
        |"file": {
        |  "id": "${regexes.digitsRegex}",
        |  "status": "$fileStatus"
        |}
        |""".stripMargin
    } else {
      s"""
         |"file": {
         |  "status": "$fileStatus"
         |}
         |""".stripMargin
    }
    val response = given()
      .when()
      .basicAuthAllPermUser()
      .get(s"$nuDesignerHttpAddress/api/processes/$scenarioName/activity/activities")
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
             |    },
             |    {
             |      "id": "${regexes.looseUuidRegex}",
             |      "user": "$user",
             |      "date": "${regexes.zuluDateRegex}",
             |      "scenarioVersionId": 1,
             |      "attachment": {
             |        $fileJson,
             |        "filename": "$filename",
             |        "lastModifiedBy": "$user",
             |        "lastModifiedAt": "${regexes.zuluDateRegex}"
             |      },
             |      "additionalFields": [],
             |      "overrideDisplayableName": "$overrideDisplayableName",
             |      "type": "ATTACHMENT_ADDED"
             |    }
             |  ]
             |}
             |""".stripMargin
        )
      )
    ScenarioActivitiesResponseWrapperForAddedAttachment(response)
  }

  def verifyAttachmentAddedActivityExists(
      user: String,
      scenarioName: String,
      fileIdPresent: Boolean,
      filename: String,
      fileStatus: String,
      overrideDisplayableName: String,
  ): ValidatableResponse = {
    val fileJson = if (fileIdPresent) {
      s"""
        |"file": {
        |  "id": "${regexes.digitsRegex}",
        |  "status": "$fileStatus"
        |}
        |""".stripMargin
    } else {
      s"""
         |"file": {
         |  "status": "$fileStatus"
         |}
         |""".stripMargin
    }
    given()
      .when()
      .basicAuthAllPermUser()
      .get(s"$nuDesignerHttpAddress/api/processes/$scenarioName/activity/activities")
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
             |    },
             |    {
             |      "id": "${regexes.looseUuidRegex}",
             |      "user": "$user",
             |      "date": "${regexes.zuluDateRegex}",
             |      "scenarioVersionId": 1,
             |      "attachment": {
             |        $fileJson,
             |        "filename": "$filename",
             |        "lastModifiedBy": "$user",
             |        "lastModifiedAt": "${regexes.zuluDateRegex}"
             |      },
             |      "additionalFields": [],
             |      "overrideDisplayableName": "$overrideDisplayableName",
             |      "type": "ATTACHMENT_ADDED"
             |    }
             |  ]
             |}
             |""".stripMargin
        )
      )
  }

  def verifyEmptyCommentsAndAttachments(scenarioName: String): Unit = {
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

  def verifyAttachmentsExists(scenarioName: String): Unit = {
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
               |      "processVersionId": "${regexes.digitsRegex}",
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

object WithScenarioActivitySpecAsserts extends WithBusinessCaseRestAssuredUsersExtensions {

  final case class ScenarioActivitiesResponseWrapperForAddedAttachment(validatableResponse: ValidatableResponse) {
    def getAddedAttachmentId: String =
      validatableResponse.extractString(s"activities[1].attachment.file.id")
  }

}
