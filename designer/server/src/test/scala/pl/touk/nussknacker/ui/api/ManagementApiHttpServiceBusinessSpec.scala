package pl.touk.nussknacker.ui.api

import io.circe.syntax.EncoderOps
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.development.manager.MockableDeploymentManagerProvider.MockableDeploymentManager
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{
  WithBusinessCaseRestAssuredUsersExtensions,
  WithMockableDeploymentManager,
  WithSimplifiedDesignerConfig
}
import pl.touk.nussknacker.test.{
  NuRestAssureExtensions,
  NuRestAssureMatchers,
  RestAssuredVerboseLoggingIfValidationFails
}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter.toScenarioGraph

import java.util.UUID

class ManagementApiHttpServiceBusinessSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithSimplifiedConfigScenarioHelper
    with WithMockableDeploymentManager
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails {

  "The endpoint for testing with parameters should" - {
    "return empty results when empty results are returned by the deployment manager" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
          MockableDeploymentManager.configureTestResults(
            Map(
              exampleScenario.name.value -> TestResults(
                nodeResults = Map.empty,
                invocationResults = Map.empty,
                externalInvocationResults = Map.empty,
                exceptions = List.empty,
              )
            )
          )
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(s"""{
            | "sourceParameters": {
            |   "sourceId": "1",
            |   "parameterExpressions": {
            |     "param1": {
            |       "language": "spel",
            |       "expression": "1"
            |     },
            |     "param2": {
            |       "language": "spel",
            |       "expression": "test"
            |     }
            |   }
            | },
            | "scenarioGraph": ${toScenarioGraph(exampleScenario).asJson.spaces2}
            |}""".stripMargin)
        .post(s"$nuDesignerHttpAddress/api/processManagement/testWithParameters/${exampleScenario.name}")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          s"""
             |{
             |  "results": {
             |    "nodeResults": {},
             |    "invocationResults": {},
             |    "externalInvocationResults": {},
             |    "exceptions": []
             |  },
             |  "counts": {
             |    "sourceId": {
             |      "all": 0,
             |      "errors": 0,
             |      "fragmentCounts": {}
             |    },
             |    "sinkId": {
             |      "all": 0,
             |      "errors": 0,
             |      "fragmentCounts": {}
             |    }
             |  }
             |}
             |""".stripMargin
        )
    }
  }

  private lazy val exampleScenarioName = UUID.randomUUID().toString

  private lazy val exampleScenario = ScenarioBuilder
    .streaming(exampleScenarioName)
    .source("sourceId", "barSource")
    .emptySink("sinkId", "barSink")

}
