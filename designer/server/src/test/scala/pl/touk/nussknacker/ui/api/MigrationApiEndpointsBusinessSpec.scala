package pl.touk.nussknacker.ui.api

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.test.base.it.{NuItTest, WithRichConfigScenarioHelper, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{
  WithMockableDeploymentManager,
  WithRichConfigRestAssuredUsersExtensions,
  WithRichDesignerConfig,
  WithSimplifiedConfigRestAssuredUsersExtensions,
  WithSimplifiedDesignerConfig
}
import pl.touk.nussknacker.test.{NuRestAssureExtensions, NuRestAssureMatchers, RestAssuredVerboseLogging}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import io.circe._
import io.circe.syntax._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import pl.touk.nussknacker.test.config.WithRichDesignerConfig.TestCategory.Category1
import pl.touk.nussknacker.test.utils.domain.ProcessTestData

class MigrationApiEndpointsBusinessSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithRichDesignerConfig
    with WithRichConfigScenarioHelper
    with WithMockableDeploymentManager
    with WithRichConfigRestAssuredUsersExtensions
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging {

  private val exampleProcessName = ProcessTestData.sampleProcessName
  private val exampleScenario    = ProcessTestData.validProcess
  private val exampleGraph       = CanonicalProcessConverter.toScenarioGraph(exampleScenario)

  private val validationResult = ValidationResult.success

  private val requestJsonBodyPlain: String =
    s"""
       |{
       |  "sourceEnvironmentId": "DEV",
       |  "processingMode": "Request-Response",
       |  "engineSetupName": "Flink",
       |  "scenarioWithDetailsForMigrations": {
       |    "name": "${exampleProcessName}",
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

  "The endpoint for scenario migration between environments should" - {
    "be OK" in {
      given()
        .applicationState(
          createSavedScenario(exampleScenario, Category1)
        )
        .when()
        .basicAuthAdmin()
        .jsonBody(requestJsonBodyPlain)
        .post(s"$nuDesignerHttpAddress/api/migrate")
        .Then()
        .equalsPlainBody("")
        .statusCode(200)
    }
    "fail when scenario is archived on target environment" in {
      given()
        .applicationState(
          createArchivedExampleScenario(exampleProcessName, Category1)
        )
        .when()
        .basicAuthAdmin()
        .jsonBody(requestJsonBodyPlain)
        .post(s"$nuDesignerHttpAddress/api/migrate")
        .Then()
        .statusCode(500)
        .equalsPlainBody(
          s"Cannot migrate, scenario ${exampleProcessName.value} is archived on test. You have to unarchive scenario on test in order to migrate."
        )
    }
  }

}
