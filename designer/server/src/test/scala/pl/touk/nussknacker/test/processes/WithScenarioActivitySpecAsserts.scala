package pl.touk.nussknacker.test.processes

import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import io.restassured.response.ValidatableResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.test.NuRestAssureMatchers
import pl.touk.nussknacker.test.base.it.NuItTest
import pl.touk.nussknacker.test.config.{WithBusinessCaseRestAssuredUsersExtensions, WithDesignerConfig}

trait WithScenarioActivitySpecAsserts
    extends AnyFreeSpecLike
    with NuItTest
    with WithDesignerConfig
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureMatchers {

  implicit class VerifyCommentExists[T <: ValidatableResponse](validatableResponse: T) {

    def verifyCommentExists(scenarioName: String, commentContent: String, commentUser: String): ValidatableResponse = {
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
