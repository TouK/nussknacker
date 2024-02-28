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

class MigrationApiEndpointsSecuritySpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithRichDesignerConfig
    with WithRichConfigScenarioHelper
    with WithMockableDeploymentManager
    with WithRichConfigRestAssuredUsersExtensions
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging {

  private val sourceEnvironmentId = "DEV"

  private val exampleProcessName = ProcessName("test")

  private val exampleScenario =
    ScenarioBuilder
      .withCustomMetaData(exampleProcessName.value, Map("environment" -> "test"))
      .source("source", "csv-source-lite")
      .emptySink("sink", "dead-end-lite")

  private val exampleGraph = CanonicalProcessConverter.toScenarioGraph(exampleScenario)

  private val validationResult = ValidationResult.success

  private def prepareRequestJsonBodyPlain(scenarioName: String): String =
    s"""
       |{
       |  "sourceEnvironmentId": "$sourceEnvironmentId",
       |  "processingMode": "Unbounded-Stream",
       |  "engineSetupName": "Flink",
       |  "scenarioWithDetailsForMigrations": {
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

  private val requestJsonBodyPlain: String = prepareRequestJsonBodyPlain(exampleProcessName.value)

  "The endpoint for scenario migration between environments should" - {
    "authorize user with all permissions" in {
      given()
        .applicationState(
          createSavedScenario(exampleScenario, Category1)
        )
        .when()
        .basicAuthAllPermUser()
        .jsonBody(requestJsonBodyPlain)
        .post(s"$nuDesignerHttpAddress/api/migrate")
        .Then()
        .statusCode(200)
    }
    "reject other user" in {
      given()
        .applicationState(
          createSavedScenario(exampleScenario, Category1)
        )
        .when()
        .basicAuthReader()
        .jsonBody(requestJsonBodyPlain)
        .post(s"$nuDesignerHttpAddress/api/migrate")
        .Then()
        .statusCode(401)
        .equalsPlainBody("The supplied user [reader] is not authorized to access this resource")
    }
  }

}
