package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.test.{NuRestAssureMatchers, RestAssuredVerboseLoggingIfValidationFails}
import pl.touk.nussknacker.test.base.it.{NuItTest, WithAccessControlCheckingConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{
  WithAccessControlCheckingConfigRestAssuredUsersExtensions,
  WithAccessControlCheckingDesignerConfig,
  WithMockableDeploymentManager
}
import pl.touk.nussknacker.test.config.WithAccessControlCheckingDesignerConfig.TestCategory.{Category1, Category2}

class ScenarioActivityApiHttpServiceSecuritySpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithAccessControlCheckingDesignerConfig
    with WithAccessControlCheckingConfigScenarioHelper
    with WithMockableDeploymentManager
    with WithAccessControlCheckingConfigRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails {

  private val commentContent = "test message"
  private val fileContent    = "very important content"
  private val fileName       = "important_file.txt"

  "The scenario activity endpoint when" - {
    "authenticated should" - {
      "return response for scenario in allowed category for the given user" in {
        val allowedScenarioName = "s1"
        given()
          .applicationState {
            createSavedScenario(exampleScenario(allowedScenarioName), category = Category1)
            createSavedScenario(exampleScenario("s2"), category = Category2)
          }
          .when()
          .basicAuthLimitedReader()
          .get(s"$nuDesignerHttpAddress/api/processes/$allowedScenarioName/activity")
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
      "return forbidden for scenario in disallowed category for the given user" in {
        val disallowedScenarioName = "s2"
        given()
          .applicationState {
            createSavedScenario(exampleScenario("s1"), category = Category1)
            createSavedScenario(exampleScenario(disallowedScenarioName), category = Category2)
          }
          .when()
          .basicAuthLimitedReader()
          .get(s"$nuDesignerHttpAddress/api/processes/$disallowedScenarioName/activity")
          .Then()
          .statusCode(403)
          .equalsPlainBody("The supplied authentication is not authorized to access this resource")
      }
    }
    "not authenticated should" - {
      "forbid access" in {
        val scenarioName = "s1"
        given()
          .applicationState {
            createSavedScenario(exampleScenario(scenarioName), category = Category1)
          }
          .when()
          .basicAuthUnknownUser()
          .get(s"$nuDesignerHttpAddress/api/processes/$scenarioName/activity")
          .Then()
          .statusCode(401)
          .equalsPlainBody("The supplied authentication is invalid")
      }
    }
    "no credentials were passed should" - {
      "authenticate as anonymous and return response for scenario related to anonymous role category" in {
        val allowedScenarioName = "s2"
        given()
          .applicationState {
            createSavedScenario(exampleScenario("s1"), category = Category1)
            createSavedScenario(exampleScenario(allowedScenarioName), category = Category2)
          }
          .when()
          .noAuth()
          .get(s"$nuDesignerHttpAddress/api/processes/$allowedScenarioName/activity")
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
    }
  }

  "The scenario add comment endpoint when" - {
    "authenticated should" - {
      "allow to add comment in scenario in allowed category for the given user" in {
        val allowedScenarioName = "s1"
        given()
          .applicationState {
            createSavedScenario(exampleScenario(allowedScenarioName), category = Category1)
            createSavedScenario(exampleScenario("s2"), category = Category2)
          }
          .when()
          .basicAuthLimitedWriter()
          .plainBody(commentContent)
          .post(s"$nuDesignerHttpAddress/api/processes/$allowedScenarioName/1/activity/comments")
          .Then()
          .statusCode(200)
          .equalsPlainBody("")
      }
      "forbid to add comment in scenario in forbidden category for the given user" in {
        val disallowedScenarioName = "s2"
        given()
          .applicationState {
            createSavedScenario(exampleScenario("s1"), category = Category1)
            createSavedScenario(exampleScenario(disallowedScenarioName), category = Category2)
          }
          .plainBody(commentContent)
          .basicAuthLimitedWriter()
          .when()
          .post(s"$nuDesignerHttpAddress/api/processes/$disallowedScenarioName/1/activity/comments")
          .Then()
          .statusCode(403)
          .equalsPlainBody("The supplied authentication is not authorized to access this resource")
      }
      "forbid to add comment in scenario because of no writer permission" in {
        val allowedScenarioName = "s1"
        given()
          .applicationState {
            createSavedScenario(exampleScenario(allowedScenarioName), category = Category1)
            createSavedScenario(exampleScenario("s2"), category = Category2)
          }
          .plainBody(commentContent)
          .basicAuthLimitedReader()
          .when()
          .post(s"$nuDesignerHttpAddress/api/processes/$allowedScenarioName/1/activity/comments")
          .Then()
          .statusCode(403)
          .equalsPlainBody("The supplied authentication is not authorized to access this resource")
      }
    }
    "not authenticated should" - {
      "forbid access" in {
        val scenarioName = "s1"
        given()
          .applicationState {
            createSavedScenario(exampleScenario(scenarioName), category = Category1)
          }
          .when()
          .basicAuthUnknownUser()
          .plainBody(commentContent)
          .post(s"$nuDesignerHttpAddress/api/processes/$scenarioName/1/activity/comments")
          .Then()
          .statusCode(401)
          .equalsPlainBody("The supplied authentication is invalid")
      }
    }
    "no credentials were passed should" - {
      "authenticate as anonymous and forbid to add a comment because of writer permission" in {
        val allowedScenarioName = "s2"
        given()
          .applicationState {
            createSavedScenario(exampleScenario("s1"), category = Category1)
            createSavedScenario(exampleScenario(allowedScenarioName), category = Category2)
          }
          .plainBody(commentContent)
          .noAuth()
          .when()
          .post(s"$nuDesignerHttpAddress/api/processes/$allowedScenarioName/1/activity/comments")
          .Then()
          .statusCode(403)
          .equalsPlainBody("The supplied authentication is not authorized to access this resource")
      }
    }
    "impersonating user has permission to impersonate should" - {
      val allowedScenarioName = "s1"
      "allow to add comment in scenario in allowed category for the impersonated user" in {
        given()
          .applicationState {
            createSavedScenario(exampleScenario(allowedScenarioName), category = Category1)
            createSavedScenario(exampleScenario("s2"), category = Category2)
          }
          .when()
          .basicAuthAllPermUser()
          .impersonateLimitedWriterUser()
          .plainBody(commentContent)
          .post(s"$nuDesignerHttpAddress/api/processes/$allowedScenarioName/1/activity/comments")
          .Then()
          .statusCode(200)
          .equalsPlainBody("")
      }
      "forbid to add comment in scenario because impersonated user has no writer permission" in {
        given()
          .applicationState {
            createSavedScenario(exampleScenario(allowedScenarioName), category = Category1)
            createSavedScenario(exampleScenario("s2"), category = Category2)
          }
          .basicAuthAllPermUser()
          .impersonateLimitedReaderUser()
          .plainBody(commentContent)
          .when()
          .post(s"$nuDesignerHttpAddress/api/processes/$allowedScenarioName/1/activity/comments")
          .Then()
          .statusCode(403)
          .equalsPlainBody("The supplied authentication is not authorized to access this resource")
      }
      "forbid admin impersonation with default configuration" in {
        given()
          .applicationState {
            createSavedScenario(exampleScenario(allowedScenarioName), category = Category1)
            createSavedScenario(exampleScenario("s2"), category = Category2)
          }
          .when()
          .basicAuthAllPermUser()
          .impersonateAdminUser()
          .plainBody(commentContent)
          .post(s"$nuDesignerHttpAddress/api/processes/$allowedScenarioName/1/activity/comments")
          .Then()
          .statusCode(403)
          .equalsPlainBody("The supplied authentication is not authorized to impersonate")
      }
    }
    "impersonating user does not have permission to impersonate should" - {
      "forbid access" in {
        val allowedScenarioName = "s1"
        given()
          .applicationState {
            createSavedScenario(exampleScenario(allowedScenarioName), category = Category1)
            createSavedScenario(exampleScenario("s2"), category = Category2)
          }
          .plainBody(commentContent)
          .basicAuthWriter()
          .impersonateLimitedReaderUser()
          .when()
          .post(s"$nuDesignerHttpAddress/api/processes/$allowedScenarioName/1/activity/comments")
          .Then()
          .statusCode(403)
          .equalsPlainBody("The supplied authentication is not authorized to impersonate")
      }
    }
  }

  "The scenario remove comment endpoint when" - {
    "authenticated should" - {
      "allow to remove comment in scenario in allowed category for the given user" in {
        val allowedScenarioName = "s1"
        val commentId = given()
          .applicationState {
            createSavedScenario(exampleScenario(allowedScenarioName), category = Category1)
            createSavedScenario(exampleScenario("s2"), category = Category2)
            createComment(scenarioName = allowedScenarioName, commentContent = commentContent)
          }
          .when()
          .basicAuthLimitedReader()
          .get(s"$nuDesignerHttpAddress/api/processes/$allowedScenarioName/activity")
          .Then()
          .extractLong("comments[0].id")

        given()
          .when()
          .basicAuthLimitedWriter()
          .delete(s"$nuDesignerHttpAddress/api/processes/$allowedScenarioName/activity/comments/$commentId")
          .Then()
          .statusCode(200)
      }
      "forbid to remove comment in scenario in disallowed category for the given user" in {
        val disallowedScenarioName = "s2"
        given()
          .applicationState {
            createSavedScenario(exampleScenario("s1"), category = Category1)
            createSavedScenario(exampleScenario(disallowedScenarioName), category = Category2)
            createComment(scenarioName = disallowedScenarioName, commentContent = commentContent)
          }
          .basicAuthLimitedWriter()
          .when()
          .delete(s"$nuDesignerHttpAddress/api/processes/$disallowedScenarioName/activity/comments/1")
          .Then()
          .statusCode(403)
          .equalsPlainBody("The supplied authentication is not authorized to access this resource")
      }
      "forbid to remove comment in scenario because of no writer permission" in {
        val allowedScenarioName = "s1"
        given()
          .applicationState {
            createSavedScenario(exampleScenario(allowedScenarioName), category = Category1)
            createSavedScenario(exampleScenario("s2"), category = Category2)
            createComment(scenarioName = allowedScenarioName, commentContent = commentContent)
          }
          .basicAuthLimitedReader()
          .when()
          .delete(s"$nuDesignerHttpAddress/api/processes/$allowedScenarioName/activity/comments/1")
          .Then()
          .statusCode(403)
          .equalsPlainBody("The supplied authentication is not authorized to access this resource")
      }
    }
    "not authenticated should" - {
      "forbid access" in {
        val scenarioName = "s1"
        given()
          .applicationState {
            createSavedScenario(exampleScenario(scenarioName), category = Category1)
          }
          .when()
          .basicAuthUnknownUser()
          .delete(s"$nuDesignerHttpAddress/api/processes/$scenarioName/activity/comments/1")
          .Then()
          .statusCode(401)
          .equalsPlainBody("The supplied authentication is invalid")
      }
    }
    "no credentials were passed should" - {
      "authenticate as anonymous and forbid to delete a comment because of writer permission" in {
        val allowedScenarioName = "s2"
        given()
          .applicationState {
            createSavedScenario(exampleScenario("s1"), category = Category1)
            createSavedScenario(exampleScenario(allowedScenarioName), category = Category2)
            createComment(scenarioName = allowedScenarioName, commentContent = commentContent)
          }
          .basicAuthLimitedReader()
          .when()
          .delete(s"$nuDesignerHttpAddress/api/processes/$allowedScenarioName/activity/comments/1")
          .Then()
          .statusCode(403)
          .equalsPlainBody("The supplied authentication is not authorized to access this resource")
      }
    }
  }

  "The scenario add an attachment endpoint when" - {
    "authenticated should" - {
      "allow to add an attachment in scenario in allowed category for the given user" in {
        val allowedScenarioName = "s1"
        given()
          .applicationState {
            createSavedScenario(exampleScenario(allowedScenarioName), category = Category1)
            createSavedScenario(exampleScenario("s2"), category = Category2)
          }
          .streamBody(fileContent = fileContent, fileName = fileName)
          .basicAuthLimitedWriter()
          .when()
          .post(s"$nuDesignerHttpAddress/api/processes/$allowedScenarioName/1/activity/attachments")
          .Then()
          .statusCode(200)
          .equalsPlainBody("")
      }
      "forbid to add an attachment in scenario in disallowed category for the given user" in {
        val disallowedScenarioName = "s2"
        given()
          .applicationState {
            createSavedScenario(exampleScenario("s1"), category = Category1)
            createSavedScenario(exampleScenario(disallowedScenarioName), category = Category2)
          }
          .streamBody(fileContent = "test", fileName = "test.xml")
          .basicAuthLimitedWriter()
          .when()
          .post(s"$nuDesignerHttpAddress/api/processes/$disallowedScenarioName/1/activity/attachments")
          .Then()
          .statusCode(403)
          .equalsPlainBody("The supplied authentication is not authorized to access this resource")
      }
      "forbid to add an attachment in scenario because of no writer permission" in {
        val allowedScenarioName = "s1"
        given()
          .applicationState {
            createSavedScenario(exampleScenario(allowedScenarioName), category = Category1)
            createSavedScenario(exampleScenario("s2"), category = Category2)
          }
          .streamBody(fileContent = "test", fileName = "test.xml")
          .basicAuthLimitedReader()
          .when()
          .post(s"$nuDesignerHttpAddress/api/processes/$allowedScenarioName/1/activity/attachments")
          .Then()
          .statusCode(403)
          .equalsPlainBody("The supplied authentication is not authorized to access this resource")
      }
    }
    "not authenticated should" - {
      "forbid access" in {
        val scenarioName = "s1"
        given()
          .applicationState {
            createSavedScenario(exampleScenario(scenarioName), category = Category1)
          }
          .when()
          .basicAuthUnknownUser()
          .streamBody(fileContent = "test", fileName = "test.xml")
          .post(s"$nuDesignerHttpAddress/api/processes/$scenarioName/1/activity/attachments")
          .Then()
          .statusCode(401)
          .equalsPlainBody("The supplied authentication is invalid")
      }
    }
    "no credentials were passed should" - {
      "authenticate as anonymous and forbid to add an attachment because of writer permission" in {
        val allowedScenarioName = "s2"
        given()
          .applicationState {
            createSavedScenario(exampleScenario("s1"), category = Category1)
            createSavedScenario(exampleScenario(allowedScenarioName), category = Category2)
          }
          .streamBody(fileContent = "test", fileName = "test.xml")
          .basicAuthLimitedReader()
          .when()
          .post(s"$nuDesignerHttpAddress/api/processes/$allowedScenarioName/1/activity/attachments")
          .Then()
          .statusCode(403)
          .equalsPlainBody("The supplied authentication is not authorized to access this resource")
      }
    }
  }

  "The scenario download attachment endpoint when" - {
    "authenticated should" - {
      "allow to download attachment in scenario in allowed category for the given user" in {
        val allowedScenarioName = "s1"
        val attachmentId = given()
          .applicationState {
            createSavedScenario(exampleScenario(allowedScenarioName), category = Category1)
            createSavedScenario(exampleScenario("s2"), category = Category2)
            createAttachment(scenarioName = allowedScenarioName, fileContent = fileContent)
          }
          .when()
          .basicAuthLimitedReader()
          .get(s"$nuDesignerHttpAddress/api/processes/$allowedScenarioName/activity")
          .Then()
          .extractLong("attachments[0].id")

        given()
          .basicAuthLimitedWriter()
          .when()
          .get(s"$nuDesignerHttpAddress/api/processes/$allowedScenarioName/activity/attachments/$attachmentId")
          .Then()
          .statusCode(200)
          .equalsPlainBody(fileContent)
      }
      "forbid to download attachment in scenario in disallowed category for the given user" in {
        val disallowedScenarioName = "s2"
        given()
          .applicationState {
            createSavedScenario(exampleScenario("s1"), category = Category1)
            createSavedScenario(exampleScenario(disallowedScenarioName), category = Category2)
            createAttachment(scenarioName = disallowedScenarioName, fileContent = fileContent)
          }
          .when()
          .basicAuthLimitedReader()
          .plainBody(commentContent)
          .get(s"$nuDesignerHttpAddress/api/processes/$disallowedScenarioName/activity/attachments/1")
          .Then()
          .statusCode(403)
          .equalsPlainBody("The supplied authentication is not authorized to access this resource")
      }
    }
    "not authenticated should" - {
      "forbid access" in {
        val scenarioName = "s1"
        given()
          .applicationState {
            createSavedScenario(exampleScenario(scenarioName), category = Category1)
          }
          .when()
          .basicAuthUnknownUser()
          .get(s"$nuDesignerHttpAddress/api/processes/$scenarioName/activity/attachments/1")
          .Then()
          .statusCode(401)
          .equalsPlainBody("The supplied authentication is invalid")
      }
    }
    "no credentials were passed should" - {
      "authenticate as anonymous and download attachment for scenario related to anonymous role category" in {
        val allowedScenarioName = "s2"
        val attachmentId = given()
          .applicationState {
            createSavedScenario(exampleScenario("s1"), category = Category1)
            createSavedScenario(exampleScenario(allowedScenarioName), category = Category2)
            createAttachment(scenarioName = allowedScenarioName, fileContent = fileContent)
          }
          .when()
          .noAuth()
          .get(s"$nuDesignerHttpAddress/api/processes/$allowedScenarioName/activity")
          .Then()
          .extractLong("attachments[0].id")

        given()
          .when()
          .noAuth()
          .get(s"$nuDesignerHttpAddress/api/processes/$allowedScenarioName/activity/attachments/$attachmentId")
          .Then()
          .statusCode(200)
          .equalsPlainBody(fileContent)
      }
    }
  }

  private def exampleScenario(scenarioName: String) = ScenarioBuilder
    .streaming(scenarioName)
    .source("sourceId", "barSource")
    .emptySink("sinkId", "barSink")

  private def createComment(scenarioName: String, commentContent: String): Unit = {
    given()
      .plainBody(commentContent)
      .basicAuthAdmin()
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
      .basicAuthAdmin()
      .when()
      .post(s"$nuDesignerHttpAddress/api/processes/$scenarioName/1/activity/attachments")
  }

}
