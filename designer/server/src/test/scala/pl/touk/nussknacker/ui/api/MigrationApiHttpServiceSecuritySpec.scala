package pl.touk.nussknacker.ui.api

import io.circe.syntax._
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.test.base.it.{
  NuItTest,
  WithAccessControlCheckingConfigScenarioHelper,
  WithSimplifiedConfigScenarioHelper
}
import pl.touk.nussknacker.test.config.WithAccessControlCheckingDesignerConfig.TestCategory.Category1
import pl.touk.nussknacker.test.config.{
  WithAccessControlCheckingConfigRestAssuredUsersExtensions,
  WithAccessControlCheckingDesignerConfig,
  WithBusinessCaseRestAssuredUsersExtensions,
  WithMockableDeploymentManager
}
import pl.touk.nussknacker.test.{NuRestAssureExtensions, NuRestAssureMatchers, RestAssuredVerboseLogging}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter

class MigrationApiHttpServiceSecuritySpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithAccessControlCheckingDesignerConfig
    with WithAccessControlCheckingConfigScenarioHelper
    with WithAccessControlCheckingConfigRestAssuredUsersExtensions
    with WithMockableDeploymentManager
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging {

  "The endpoint for scenario migration between environments when" - {
    "authenticated should" - {
      "return response for allowed scenario" in {
        given()
          .applicationState(
            createSavedScenario(exampleScenario, Category1)
          )
          .when()
          .basicAuthAllPermUser()
          .jsonBody(requestData)
          .post(s"$nuDesignerHttpAddress/api/migrate")
          .Then()
          .statusCode(200)
          .equalsPlainBody("")
      }
    }
    "not authenticated should" - {
      "forbid access for user with limited reading permissions" in {
        given()
          .applicationState(
            createSavedScenario(exampleScenario, Category1)
          )
          .when()
          .basicAuthReader()
          .jsonBody(requestData)
          .post(s"$nuDesignerHttpAddress/api/migrate")
          .Then()
          .statusCode(401)
          .equalsPlainBody("User doesn't have access to the given category")
      }
      "forbid access for user with limited writing permissions" in {
        given()
          .applicationState(
            createSavedScenario(exampleScenario, Category1)
          )
          .when()
          .basicAuthWriter()
          .jsonBody(requestData)
          .post(s"$nuDesignerHttpAddress/api/migrate")
          .Then()
          .statusCode(401)
          .equalsPlainBody("The supplied user [writer] is not authorized to access this resource")
      }
    }
    "no credentials were passes should" - {
      "forbid access" in {
        given()
          .applicationState(
            createSavedScenario(exampleScenario, Category1)
          )
          .when()
          .noAuth()
          .jsonBody(requestData)
          .post(s"$nuDesignerHttpAddress/api/migrate")
          .Then()
          .statusCode(401)
          .equalsPlainBody("User doesn't have access to the given category")
      }
    }
  }

  private lazy val sourceEnvironmentId = "DEV"

  private lazy val exampleProcessName = ProcessName("test")

  private lazy val exampleScenario =
    ScenarioBuilder
      .withCustomMetaData(exampleProcessName.value, Map("environment" -> "test"))
      .source("source", "csv-source-lite")
      .emptySink("sink", "dead-end-lite")

  private lazy val exampleGraph = CanonicalProcessConverter.toScenarioGraph(exampleScenario)

  private def prepareRequestData(scenarioName: String): String =
    s"""
       |{
       |  "sourceEnvironmentId": "$sourceEnvironmentId",
       |  "processingMode": "Unbounded-Stream",
       |  "remoteUserName": "remoteUser",
       |  "engineSetupName": "Mockable",
       |  "processName": "${scenarioName}",
       |  "isFragment": false,
       |  "processCategory": "${Category1.stringify}",
       |  "scenarioGraph": ${exampleGraph.asJson.noSpaces}
       |}
       |""".stripMargin

  private lazy val requestData: String = prepareRequestData(exampleProcessName.value)

}
