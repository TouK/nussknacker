package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.test.{NuRestAssureMatchers, RestAssuredVerboseLoggingIfValidationFails}
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{WithBusinessCaseRestAssuredUsersExtensions, WithSimplifiedDesignerConfig}

class ActionInfoHttpServiceBusinessSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithSimplifiedConfigScenarioHelper
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails
    with Matchers {

  "The scenario action info endpoint when" - {
    "return action parameters when defined" in {
      val scenario = ScenarioBuilder
        .streaming("scenarioWithSourceWithDeployParameters")
        .source("sourceWithParametersId", "boundedSourceWithOffset", "elements" -> "{'one', 'two', 'three'}".spel)
        .emptySink("exampleSinkId", "monitor")

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
             |  "actionNameToParameters":{
             |    "DEPLOY":[
             |      {
             |        "nodeId":"sourceWithParametersId",
             |        "componentId": "boundedSourceWithOffset",
             |        "parameters":{
             |          "offset":{
             |            "defaultValue":null,
             |            "editor":{"type":"StaticStringParameterEditor"},
             |            "label":"Offset",
             |            "hintText":"Set offset to setup source to emit elements from specified start point in input collection. Empty field resets collection to the beginning."
             |          }
             |        }
             |      }
             |    ]
             |  }
             |}""".stripMargin)
    }

    "return action parameters for processors" in {
      val scenario = ScenarioBuilder
        .streaming("scenarioWithSourceWithDeployParameters")
        .source("sourceWithParametersId", "boundedSourceWithOffset", "elements" -> "{'one', 'two', 'three'}".spel)
        .processor(
          "logging1",
          "log",
          "message" -> "Hello #{#input}".spelTemplate,
          "logger"  -> "'test'".spel,
          "level"   -> "T(org.slf4j.event.Level).DEBUG".spel
        )
        .buildSimpleVariable("var1", "var1", "'there!'".spel)
        .processor(
          "logging2",
          "log",
          "message" -> "Hello #{#var1}".spelTemplate,
          "logger"  -> "'test'".spel,
          "level"   -> "T(org.slf4j.event.Level).DEBUG".spel
        )
        .customNode("stateful", "stateVar", "constantStateTransformer")
        .emptySink("end", "monitor")

      given()
        .applicationState {
          createSavedScenario(scenario)
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/actionInfo/${scenario.name.value}/parameters")
        .Then()
        .statusCode(200)
        .extractList[java.util.Map[String, Any]]("actionNameToParameters.DEPLOY")
        .map(_.get("nodeId"))
        .toSet shouldBe Set("sourceWithParametersId", "logging1", "logging2")
    }

    "return action parameters for scenario with fragment" in {
      val fragment = ScenarioBuilder
        .fragment("fragment1", "in" -> classOf[String])
        .filter("filter", "#in != 'stop'".spel)
        .fragmentOutput("fragmentEnd", "output", "out" -> "#in".spel)
      val scenario = ScenarioBuilder
        .streaming("scenarioWithSourceWithDeployParameters")
        .source("sourceWithParametersId", "boundedSourceWithOffset", "elements" -> "{'one', 'two', 'three'}".spel)
        .buildSimpleVariable("var1", "in", "'start!'".spel)
        .fragmentOneOut("fragment1", "fragment1", "output", "output", "in" -> "#in".spel)
        .emptySink("end", "monitor")

      given()
        .applicationState {
          createSavedFragment(fragment)
          createSavedScenario(scenario)
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/actionInfo/${scenario.name.value}/parameters")
        .Then()
        .statusCode(200)
        .equalsJsonBody("""|{
                           |  "actionNameToParameters":{
                           |    "DEPLOY":[
                           |      {
                           |        "nodeId":"sourceWithParametersId",
                           |        "componentId": "boundedSourceWithOffset",
                           |        "parameters":{
                           |          "offset":{
                           |            "defaultValue":null,
                           |            "editor":{"type":"StaticStringParameterEditor"},
                           |            "label":"Offset",
                           |            "hintText":"Set offset to setup source to emit elements from specified start point in input collection. Empty field resets collection to the beginning."
                           |          }
                           |        }
                           |      }
                           |    ]
                           |  }
                           |}""".stripMargin)
    }

    "return empty map when no action parameters" in {
      val scenario = ScenarioBuilder
        .streaming("scenarioWithoutParameters")
        .source("sourceNoParamsId", "boundedSource", "elements" -> "{'one', 'two', 'three'}".spel)
        .emptySink("exampleSinkId", "monitor")

      given()
        .applicationState {
          createSavedScenario(scenario)
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/actionInfo/${scenario.name.value}/parameters")
        .Then()
        .statusCode(200)
        .equalsJsonBody("""{"actionNameToParameters":{}}""")
    }

    "return no data found when there is no scenario" in {
      val scenario = ScenarioBuilder
        .streaming("invalidScenario")
        .source("exampleSource", "boundedSource", "elements" -> "{'one', 'two', 'three'}".spel)
        .emptySink("exampleSinkId", "monitor")

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

    "return cannot compile scenario where scenarios has errors" in {
      val scenario = ScenarioBuilder
        .streaming("invalidScenario")
        .source("exampleSource", "boundedSource", "elements" -> "{'one', 'two', 'three'}".spel)
        .emptySink("exampleSinkId", "emptySink")

      given()
        .applicationState {
          createSavedScenario(scenario)
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/actionInfo/${scenario.name.value}/parameters")
        .Then()
        .statusCode(400)
        .equalsPlainBody("Cannot compile scenario")
    }
  }

}
