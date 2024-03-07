package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.`given`
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.development.manager.MockableDeploymentManagerProvider
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.test.NuRestAssureExtensions.AppConfiguration
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.{NuRestAssureMatchers, RestAssuredVerboseLogging}
import pl.touk.nussknacker.test.config.{WithSimplifiedConfigRestAssuredUsersExtensions, WithSimplifiedDesignerConfig}

class ManagementApiHttpServiceSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithSimplifiedConfigScenarioHelper
    with WithSimplifiedConfigRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging {

  "The endpoint for nodes validation should " - {
    "validate proper request without errors " in {
      given()
        .applicationState {
          createDeployedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(
          s"""{
          | "actionName": "run_now",
          | "params": {
          |  "
          |}""".stripMargin
        )

    }
  }

  private lazy val exampleScenario = ScenarioBuilder
    .streaming("test")
    .source("sourceId", "barSource")
    .emptySink("sinkId", "barSink")

}
