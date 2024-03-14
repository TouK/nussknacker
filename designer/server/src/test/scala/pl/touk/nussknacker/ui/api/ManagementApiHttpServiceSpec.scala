package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.`given`
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.development.manager.MockableDeploymentManagerProvider
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.test.NuRestAssureExtensions.AppConfiguration
import pl.touk.nussknacker.test.base.it.{NuItTest, NuResourcesTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.{NuRestAssureExtensions, NuRestAssureMatchers, RestAssuredVerboseLogging}
import pl.touk.nussknacker.test.config.{
  WithMockableDeploymentManager,
  WithSimplifiedConfigRestAssuredUsersExtensions,
  WithSimplifiedDesignerConfig
}
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse

import java.util.UUID

class ManagementApiHttpServiceSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithSimplifiedConfigScenarioHelper
    with WithSimplifiedConfigRestAssuredUsersExtensions
    with WithMockableDeploymentManager
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging {

  private lazy val exampleScenarioName = UUID.randomUUID().toString

  private lazy val exampleScenario = ScenarioBuilder
    .streaming(exampleScenarioName)
    .source("sourceId", "barSource")
    .emptySink("sinkId", "barSink")

  // TODO: add tests
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
             | "actionName": "hello",
             | "params": null
             |}""".stripMargin
        )
        .post(s"$nuDesignerHttpAddress/api/processManagement/customAction/$exampleScenarioName/validation")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          s"""{
             |  "buga": "buga"
             |}
             |""".stripMargin
        )

    }
    "return error for wrong request params" in {
      given()
        .applicationState {
          createDeployedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(
          s"""{
             |  "actionName": "hello",
             |  "params": {
             |    "property1": "abc",
             |    "property2": "xyz"
             |  }
             |}
             |""".stripMargin
        )
        .post(s"$nuDesignerHttpAddress/api/processManagement/customAction/$exampleScenarioName/validation")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          s"""{
             |  "buga": "buga"
             |}
             |""".stripMargin
        )
    }
    "return error for non existing action" in {
      given()
        .applicationState {
          createDeployedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(
          s"""{
             |  "actionName": "non-existing",
             |  "params": {
             |    "property1": "abc",
             |    "property2": "xyz"
             |  }
             |}
             |""".stripMargin
        )
        .post(s"$nuDesignerHttpAddress/api/processManagement/customAction/$exampleScenarioName/validation")
        .Then()
        .statusCode(404)
        .equalsJsonBody(
          s"""{
             |  "buga": "buga"
             |}
             |""".stripMargin
        )
    }
  }

}
