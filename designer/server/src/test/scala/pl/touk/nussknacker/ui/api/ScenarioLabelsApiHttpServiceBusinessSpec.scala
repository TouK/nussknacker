package pl.touk.nussknacker.ui.api

import io.circe.syntax._
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{
  WithBusinessCaseRestAssuredUsersExtensions,
  WithMockableDeploymentManager,
  WithSimplifiedDesignerConfig
}
import pl.touk.nussknacker.test.processes.WithScenarioActivitySpecAsserts
import pl.touk.nussknacker.test.{
  NuRestAssureExtensions,
  NuRestAssureMatchers,
  RestAssuredVerboseLoggingIfValidationFails
}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter

import java.util.UUID

class ScenarioLabelsApiHttpServiceBusinessSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithSimplifiedConfigScenarioHelper
    with WithMockableDeploymentManager
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureExtensions
    with WithScenarioActivitySpecAsserts
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails {

  private val exampleScenarioName = UUID.randomUUID().toString

  private val exampleScenario = ScenarioBuilder
    .streaming(exampleScenarioName)
    .source("sourceId", "barSource")
    .emptySink("sinkId", "barSink")

  private val otherExampleScenario = ScenarioBuilder
    .streaming(UUID.randomUUID().toString)
    .source("sourceId", "barSource")
    .emptySink("sinkId", "barSink")

  "The scenario labels endpoint when" - {
    "return no labels for existing process without them" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/scenarioLabels")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          s"""
             |{
             |  "labels": []
             |}
             |""".stripMargin
        )
    }
    "return labels for all processes" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
          updateScenarioLabels(exampleScenario, List("tag2", "tag3"))
          createSavedScenario(otherExampleScenario)
          updateScenarioLabels(otherExampleScenario, List("tag1", "tag4"))
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/scenarioLabels")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          s"""
             |{
             |  "labels": ["tag1", "tag2", "tag3", "tag4"]
             |}
             |""".stripMargin
        )
    }
  }

  "The scenario labels validation endpoint when" - {
    "return no errors when" - {
      "no labels passed" in {
        given()
          .when()
          .basicAuthAllPermUser()
          .body(
            s"""
               |{
               |  "labels": []
               |}""".stripMargin
          )
          .post(s"$nuDesignerHttpAddress/api/scenarioLabels/validation")
          .Then()
          .statusCode(200)
          .equalsJsonBody(
            s"""
               |{
               |  "validationErrors": []
               |}
               |""".stripMargin
          )
      }
      "all labels are passing validation" in {
        given()
          .when()
          .basicAuthAllPermUser()
          .body(
            s"""
               |{
               |  "labels": ["tag1", "tag2", "tag3"]
               |}""".stripMargin
          )
          .post(s"$nuDesignerHttpAddress/api/scenarioLabels/validation")
          .Then()
          .statusCode(200)
          .equalsJsonBody(
            s"""
               |{
               |  "validationErrors": []
               |}
               |""".stripMargin
          )
      }
    }
    "return validation errors when" - {
      "some labels are not matching validation rules" in {
        given()
          .when()
          .basicAuthAllPermUser()
          .body(
            s"""
               |{
               |  "labels": ["tooLongTag", "tag 1", "tag 12345"]
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
               |      "label": "tooLongTag",
               |      "messages": [
               |          "Scenario label can contain up to 5 characters"
               |      ]
               |    },
               |    {
               |      "label": "tag 1",
               |      "messages": [
               |          "Scenario label can contain only alphanumeric characters"
               |      ]
               |    },
               |    {
               |      "label": "tag 12345",
               |      "messages": [
               |          "Scenario label can contain only alphanumeric characters",
               |          "Scenario label can contain up to 5 characters"
               |      ]
               |    }
               |  ]
               |}
               |""".stripMargin
          )
      }
      "labels are duplicated" in {
        given()
          .when()
          .basicAuthAllPermUser()
          .body(
            s"""
               |{
               |  "labels": ["tag1", "tag1"]
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
               |      "label": "tag1",
               |      "messages": [
               |          "Label has to be unique"
               |      ]
               |    }
               |  ]
               |}
               |""".stripMargin
          )
      }
    }
  }

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
