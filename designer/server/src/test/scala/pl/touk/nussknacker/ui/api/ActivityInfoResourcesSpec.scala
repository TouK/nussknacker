package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers.{equalTo, notNullValue}
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{WithBusinessCaseRestAssuredUsersExtensions, WithSimplifiedDesignerConfig}
import pl.touk.nussknacker.test.{NuRestAssureMatchers, RestAssuredVerboseLoggingIfValidationFails}
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.test.utils.domain.TestProcessUtil

class ActivityInfoResourcesSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithSimplifiedConfigScenarioHelper
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails {

  "The scenario activity info endpoint when" - {
    "return activity parameters when defined" in {
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
        .jsonBody(TestProcessUtil.toJson(scenario).noSpaces)
        .post(s"$nuDesignerHttpAddress/api/activityInfo/${scenario.name.value}/activityParameters")
        .Then()
        .statusCode(200)
        .body(
          "DEPLOY[0].nodeId",
          equalTo("sourceWithParametersId"),
          "DEPLOY[0].parameters.offset",
          notNullValue(),
        )
    }

    "return empty map when no activity parameters" in {
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
        .jsonBody(TestProcessUtil.toJson(scenario).noSpaces)
        .post(s"$nuDesignerHttpAddress/api/activityInfo/${scenario.name.value}/activityParameters")
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
        .jsonBody(TestProcessUtil.toJson(scenario).noSpaces)
        .post(s"$nuDesignerHttpAddress/api/activityInfo/${scenario.name.value}/activityParameters")
        .Then()
        .statusCode(404)
        .equalsPlainBody(
          s"No scenario ${scenario.name.value} found"
        )
    }
  }

}
