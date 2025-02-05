package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{WithBusinessCaseRestAssuredUsersExtensions, WithSimplifiedDesignerConfig}
import pl.touk.nussknacker.test.{NuRestAssureMatchers, RestAssuredVerboseLoggingIfValidationFails}
import pl.touk.nussknacker.engine.spel.SpelExtension._

class ActionInfoResourcesSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithSimplifiedConfigScenarioHelper
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails {

  "The scenario action info endpoint when" - {
    "return action parameters when defined" in {
      val scenario = ScenarioBuilder
        .streaming("scenarioWithSourceWithDeployParameters")
        .source("sourceWithParametersId", "boundedSourceWithOffset", "elements" -> "{'one', 'two', 'three'}".spel)
        .emptySink("exampleSinkId", "emptySink")

      given()
        .applicationState {
          createSavedScenario(scenario)
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/actionInfo/${scenario.name.value}/parameters")
        .Then()
        .statusCode(200)
        .equalsJsonBody("""|{
             |  "DEPLOY":[
             |    {
             |      "nodeId":"sourceWithParametersId",
             |      "parameters":{
             |        "offset":{
             |          "defaultValue":null,
             |          "editor":{"type":"RawParameterEditor"},
             |          "label":"Offset",
             |          "hintText":"Set offset to setup source to emit elements from specified start point in input collection. Empty field resets collection to the beginning."
             |        }
             |      }
             |    }
             |  ]
             |}""".stripMargin)
    }

    "return empty map when no action parameters" in {
      val scenario = ScenarioBuilder
        .streaming("scenarioWithoutParameters")
        .source("sourceNoParamsId", "boundedSource", "elements" -> "{'one', 'two', 'three'}".spel)
        .emptySink("exampleSinkId", "emptySink")

      given()
        .applicationState {
          createSavedScenario(scenario)
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/actionInfo/${scenario.name.value}/parameters")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          "{}"
        )
    }

    "return no data found when there is no scenario" in {
      val scenario = ScenarioBuilder
        .streaming("invalidScenario")
        .source("exampleSource", "boundedSource", "elements" -> "{'one', 'two', 'three'}".spel)
        .emptySink("exampleSinkId", "emptySink")

      given()
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/actionInfo/${scenario.name.value}/parameters")
        .Then()
        .statusCode(404)
        .equalsPlainBody(
          s"No scenario ${scenario.name.value} found"
        )
    }
  }

}
