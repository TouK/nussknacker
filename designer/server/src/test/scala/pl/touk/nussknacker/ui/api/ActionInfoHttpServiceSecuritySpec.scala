package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers.equalTo
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.test.{NuRestAssureMatchers, RestAssuredVerboseLoggingIfValidationFails}
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{WithBusinessCaseRestAssuredUsersExtensions, WithSimplifiedDesignerConfig}

class ActionInfoHttpServiceSecuritySpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithSimplifiedConfigScenarioHelper
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails {

  "The endpoint for scenario actions when" - {
    "authenticated should" - {
      "return scenario actions" in {
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
      }
    }
    "not authenticated should" - {
      "forbid access" in {
        given()
          .when()
          .basicAuthUnknownUser()
          .get(s"$nuDesignerHttpAddress/api/actionInfo/some_scenario/parameters")
          .Then()
          .statusCode(401)
          .body(equalTo("The supplied authentication is invalid"))
      }
    }
    "no credentials were passed should" - {
      "forbid access" in {
        given()
          .when()
          .noAuth()
          .get(s"$nuDesignerHttpAddress/api/actionInfo/some_scenario/parameters")
          .Then()
          .statusCode(401)
          .body(equalTo("Invalid value (missing)"))
      }
    }
  }

}
