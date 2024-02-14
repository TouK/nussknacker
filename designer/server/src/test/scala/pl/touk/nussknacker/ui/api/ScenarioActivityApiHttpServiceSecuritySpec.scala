package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.test.{NuRestAssureMatchers, RestAssuredVerboseLogging}
import pl.touk.nussknacker.tests.base.it.{NuItTest, WithRichConfigScenarioHelper}
import pl.touk.nussknacker.tests.config.WithRichDesignerConfig.TestCategory.{Category1, Category2}
import pl.touk.nussknacker.tests.config.{
  WithMockableDeploymentManager,
  WithRichConfigRestAssuredUsersExtensions,
  WithRichDesignerConfig
}

class ScenarioActivityApiHttpServiceSecuritySpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithRichDesignerConfig
    with WithRichConfigScenarioHelper
    with WithMockableDeploymentManager
    with WithRichConfigRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging {

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
          .basicAuthLimitedReader()
          .when()
          .post(s"$nuDesignerHttpAddress/api/processes/$disallowedScenarioName/1/activity/comments")
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
          .basicAuthLimitedReader()
          .when()
          .delete(s"$nuDesignerHttpAddress/api/processes/$disallowedScenarioName/activity/comments/1")
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
  }

  "The scenario add attachment endpoint when" - {
    "authenticated should" - {
      "allow to add attachment in scenario in allowed category for the given user" in {
        val allowedScenarioName = "s1"
        given()
          .applicationState {
            createSavedScenario(exampleScenario(allowedScenarioName), category = Category1)
            createSavedScenario(exampleScenario("s2"), category = Category2)
          }
          .streamBody(fileContent = fileContent, fileName = fileName)
          .preemptiveBasicAuth("writer", "writer")
          .when()
          .post(s"$nuDesignerHttpAddress/api/processes/$allowedScenarioName/1/activity/attachments")
          .Then()
          .statusCode(200)
          .equalsPlainBody("")
      }
      "forbid to add attachment in scenario in disallowed category for the given user" in {
        val disallowedScenarioName = "s2"
        given()
          .applicationState {
            createSavedScenario(exampleScenario("s1"), category = Category1)
            createSavedScenario(exampleScenario(disallowedScenarioName), category = Category2)
          }
          .streamBody(fileContent = "test", fileName = "test.xml")
          .preemptiveBasicAuth("reader", "reader")
          .when()
          .post(s"$nuDesignerHttpAddress/api/processes/$disallowedScenarioName/1/activity/attachments")
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
  }

  private def exampleScenario(scenarioName: String) = ScenarioBuilder
    .streaming(scenarioName)
    .source("sourceId", "barSource")
    .emptySink("sinkId", "barSink")

  private def createComment(scenarioName: String, commentContent: String): Unit = {
    given()
      .plainBody(commentContent)
      .basicAuthLimitedWriter()
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

}
