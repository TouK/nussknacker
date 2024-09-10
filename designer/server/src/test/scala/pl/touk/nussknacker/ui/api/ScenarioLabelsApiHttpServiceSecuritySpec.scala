package pl.touk.nussknacker.ui.api

import io.circe.syntax._
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.test.{NuRestAssureMatchers, RestAssuredVerboseLoggingIfValidationFails}
import pl.touk.nussknacker.test.base.it.{
  NuItTest,
  WithAccessControlCheckingConfigScenarioHelper,
  WithSimplifiedConfigScenarioHelper
}
import pl.touk.nussknacker.test.config.WithAccessControlCheckingDesignerConfig.TestCategory.Category1
import pl.touk.nussknacker.test.config.{
  WithAccessControlCheckingConfigRestAssuredUsersExtensions,
  WithAccessControlCheckingDesignerConfig,
  WithMockableDeploymentManager
}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter

import java.util.UUID

class ScenarioLabelsApiHttpServiceSecuritySpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithAccessControlCheckingDesignerConfig
    with WithAccessControlCheckingConfigScenarioHelper
    with WithMockableDeploymentManager
    with WithAccessControlCheckingConfigRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails {

  "The scenario labels endpoint when" - {
    "authenticated should" - {
      "return all scenario labels" in {
        val scenario1 = exampleScenario("s1")
        val scenario2 = exampleScenario("s2")

        given()
          .applicationState {
            createSavedScenario(scenario1, category = Category1)
            updateScenarioLabels(scenario1, List("tag2", "tag3"))
            createSavedScenario(scenario2, category = Category1)
            updateScenarioLabels(scenario2, List("tag1", "tag4"))
          }
          .when()
          .basicAuthAdmin()
          .get(s"$nuDesignerHttpAddress/api/scenarioLabels")
          .Then()
          .statusCode(200)
          .equalsJsonBody(
            s"""{
               |  "labels": ["tag1", "tag2", "tag3", "tag4"]
               |}""".stripMargin
          )
      }
    }
    "not authenticated should" - {
      "forbid access" in {
        given()
          .when()
          .basicAuthUnknownUser()
          .get(s"$nuDesignerHttpAddress/api/scenarioLabels")
          .Then()
          .statusCode(401)
      }
    }
    "no credentials were passed should" - {
      "authenticate as anonymous and return no labels for no write access" in {
        val scenario1 = exampleScenario("s1")
        val scenario2 = exampleScenario("s2")

        given()
          .applicationState {
            createSavedScenario(scenario1, category = Category1)
            updateScenarioLabels(scenario1, List("tag2", "tag3"))
            createSavedScenario(scenario2, category = Category1)
            updateScenarioLabels(scenario2, List("tag1", "tag4"))
          }
          .when()
          .noAuth()
          .get(s"$nuDesignerHttpAddress/api/scenarioLabels")
          .Then()
          .statusCode(200)
          .equalsJsonBody(
            """{
              |  "labels": []
              |}""".stripMargin
          )
      }
    }
    "impersonating user has permission to impersonate should" - {
      "return all scenario labels" in {
        val scenario1 = exampleScenario("s1")
        val scenario2 = exampleScenario("s2")

        given()
          .applicationState {
            createSavedScenario(scenario1, category = Category1)
            updateScenarioLabels(scenario1, List("tag2", "tag3"))
            createSavedScenario(scenario2, category = Category1)
            updateScenarioLabels(scenario2, List("tag1", "tag4"))
          }
          .when()
          .basicAuthAllPermUser()
          .impersonateLimitedWriterUser()
          .get(s"$nuDesignerHttpAddress/api/scenarioLabels")
          .Then()
          .statusCode(200)
          .equalsJsonBody(
            s"""{
               |  "labels": ["tag1", "tag2", "tag3", "tag4"]
               |}""".stripMargin
          )
      }
    }
    "impersonating user does not have permission to impersonate should" - {
      "forbid access" in {
        given()
          .when()
          .basicAuthWriter()
          .impersonateLimitedWriterUser()
          .get(s"$nuDesignerHttpAddress/api/scenarioLabels")
          .Then()
          .statusCode(403)
          .equalsPlainBody("The supplied authentication is not authorized to impersonate")
      }
    }
  }

  "The scenario labels validation endpoint when" - {
    "authenticated should" - {
      "validate scenario labels" in {
        given()
          .when()
          .basicAuthAdmin()
          .body(
            s"""
               |{
               |  "labels": ["tag100", "tag2"]
               |}""".stripMargin
          )
          .post(s"$nuDesignerHttpAddress/api/scenarioLabels/validation")
          .Then()
          .statusCode(200)
          .equalsJsonBody(
            s"""
               |{
               |  "validationErrors": [
               |    {
               |      "label": "tag100",
               |      "messages": [
               |        "Scenario label can contain up to 5 characters"
               |      ]
               |    }
               |  ]
               |}
               |""".stripMargin
          )
      }
    }
    "not authenticated should" - {
      "forbid access" in {
        given()
          .when()
          .basicAuthUnknownUser()
          .body(
            s"""
               |{
               |  "labels": ["tag1", "tag2"]
               |}""".stripMargin
          )
          .post(s"$nuDesignerHttpAddress/api/scenarioLabels/validation")
          .Then()
          .statusCode(401)
      }
    }
    "no credentials were passed should" - {
      "authenticate as anonymous and validate labels" in {
        given()
          .when()
          .noAuth()
          .body(
            s"""
               |{
               |  "labels": ["tag100", "tag2"]
               |}""".stripMargin
          )
          .post(s"$nuDesignerHttpAddress/api/scenarioLabels/validation")
          .Then()
          .statusCode(200)
          .equalsJsonBody(
            s"""
               |{
               |  "validationErrors": [
               |    {
               |      "label": "tag100",
               |      "messages": [
               |        "Scenario label can contain up to 5 characters"
               |      ]
               |    }
               |  ]
               |}
               |""".stripMargin
          )
      }
    }
    "impersonating user has permission to impersonate should" - {
      "validate labels" in {
        val scenario1 = exampleScenario("s1")
        val scenario2 = exampleScenario("s2")

        given()
          .when()
          .basicAuthAllPermUser()
          .impersonateLimitedWriterUser()
          .body(
            s"""
               |{
               |  "labels": ["tag100", "tag2"]
               |}""".stripMargin
          )
          .post(s"$nuDesignerHttpAddress/api/scenarioLabels/validation")
          .Then()
          .statusCode(200)
          .equalsJsonBody(
            s"""
               |{
               |  "validationErrors": [
               |    {
               |      "label": "tag100",
               |      "messages": [
               |        "Scenario label can contain up to 5 characters"
               |      ]
               |    }
               |  ]
               |}
               |""".stripMargin
          )
      }
    }
    "impersonating user does not have permission to impersonate should" - {
      "forbid access" in {
        given()
          .when()
          .basicAuthWriter()
          .impersonateLimitedWriterUser()
          .body(
            s"""
               |{
               |  "labels": ["tag1", "tag2"]
               |}""".stripMargin
          )
          .post(s"$nuDesignerHttpAddress/api/scenarioLabels/validation")
          .Then()
          .statusCode(403)
          .equalsPlainBody("The supplied authentication is not authorized to impersonate")
      }
    }
  }

  private def exampleScenario(scenarioName: String) = ScenarioBuilder
    .streaming(scenarioName)
    .source("sourceId", "barSource")
    .emptySink("sinkId", "barSink")

  private def updateScenarioLabels(scenario: CanonicalProcess, labels: List[String]): Unit = {
    val scenarioName  = scenario.metaData.id
    val scenarioGraph = CanonicalProcessConverter.toScenarioGraph(scenario)

    given()
      .when()
      .jsonBody(
        s"""
           |{
           |  "scenarioGraph": ${scenarioGraph.asJson.noSpaces},
           |  "scenarioLabels": ${labels.asJson.noSpaces}
           |}
           |""".stripMargin
      )
      .basicAuthAllPermUser()
      .when()
      .put(s"$nuDesignerHttpAddress/api/processes/$scenarioName")
      .Then()
      .statusCode(200)
  }

}
