package pl.touk.nussknacker.ui.api

import io.circe.syntax._
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import pl.touk.nussknacker.test.base.it.{NuItTest, WithRichConfigScenarioHelper}
import pl.touk.nussknacker.test.config.WithRichDesignerConfig.TestCategory.Category1
import pl.touk.nussknacker.test.config.{
  WithMockableDeploymentManager,
  WithRichConfigRestAssuredUsersExtensions,
  WithRichDesignerConfig
}
import pl.touk.nussknacker.test.{NuRestAssureExtensions, NuRestAssureMatchers, RestAssuredVerboseLogging}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter

class MigrationApiHttpSecuritySpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithRichDesignerConfig
    with WithRichConfigScenarioHelper
    with WithMockableDeploymentManager
    with WithRichConfigRestAssuredUsersExtensions
    with NuRestAssureExtensions
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
          .equalsPlainBody("The supplied user [reader] is not authorized to access this resource")
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
          .equalsPlainBody("The supplied user [anonymous] is not authorized to access this resource")
      }
    }
  }

  private val sourceEnvironmentId = "DEV"

  private val exampleProcessName = ProcessName("test")

  private val exampleScenario =
    ScenarioBuilder
      .withCustomMetaData(exampleProcessName.value, Map("environment" -> "test"))
      .source("source", "csv-source-lite")
      .emptySink("sink", "dead-end-lite")

  private val exampleGraph = CanonicalProcessConverter.toScenarioGraph(exampleScenario)

  private val validationResult = ValidationResult.success

  private def prepareRequestData(scenarioName: String): String =
    s"""
       |{
       |  "sourceEnvironmentId": "$sourceEnvironmentId",
       |  "processingMode": "Unbounded-Stream",
       |  "engineSetupName": "Flink",
       |  "scenarioToMigrate": {
       |    "name": "${scenarioName}",
       |    "isArchived": false,
       |    "isFragment": false,
       |    "processingType": "streaming1",
       |    "processCategory": "Category1",
       |    "scenarioGraph": ${exampleGraph.asJson.noSpaces},
       |    "validationResult": ${validationResult.asJson.noSpaces},
       |    "history": null,
       |    "modelVersion": null
       |  }
       |}
       |""".stripMargin

  private val requestData: String = prepareRequestData(exampleProcessName.value)

}
