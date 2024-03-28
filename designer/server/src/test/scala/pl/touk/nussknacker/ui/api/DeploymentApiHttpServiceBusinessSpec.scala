package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers.equalTo
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{WithBusinessCaseRestAssuredUsersExtensions, WithSimplifiedDesignerConfig}
import pl.touk.nussknacker.test.{NuRestAssureMatchers, PatientScalaFutures, RestAssuredVerboseLogging}

class DeploymentApiHttpServiceBusinessSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithSimplifiedConfigScenarioHelper
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging
    with PatientScalaFutures {

  "The endpoint for deployment requesting should" - {
    "work" in {
      val scenarioName = "test"
      val scenario = ScenarioBuilder
        .streaming(scenarioName)
        .source("source", "boundedSource", "elements" -> Expression.spel("{}"))
        .emptySink("sink", "monitor")
      val requestedDeploymentId = "some-requested-deployment-id"

      given()
        .applicationState {
          createSavedScenario(scenario)
        }
        .when()
        .basicAuthAdmin()
        .jsonBody("{}")
        .put(s"$nuDesignerHttpAddress/api/scenarios/$scenarioName/deployments/$requestedDeploymentId")
        .Then()
        .statusCode(200)
        // TODO: check that deployment was done
        .body(
          equalTo("{}")
        )
    }
  }

}
