package pl.touk.nussknacker.ui.api

import io.circe.syntax._
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{
  NodeValidationError,
  NodeValidationErrorType,
  ValidationResult
}
import pl.touk.nussknacker.test.base.it.{NuItTest, WithRichConfigScenarioHelper}
import pl.touk.nussknacker.test.config.WithRichDesignerConfig.TestCategory.Category1
import pl.touk.nussknacker.test.config.{WithMockableDeploymentManager, WithRichDesignerConfig}
import pl.touk.nussknacker.test.processes.WithRichScenarioActivitySpecAsserts
import pl.touk.nussknacker.test.{NuRestAssureExtensions, NuRestAssureMatchers, RestAssuredVerboseLogging}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter

class MigrationApiHttpBusinessSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithRichDesignerConfig
    with WithRichConfigScenarioHelper
    with WithMockableDeploymentManager
    with WithRichScenarioActivitySpecAsserts
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging {

  "The endpoint for scenario migration between environments should" - {
    "migrate scenario and add update comment" in {
      given()
        .applicationState(
          createSavedScenario(exampleScenario, Category1)
        )
        .when()
        .basicAuthAllPermUser()
        .jsonBody(validRequestData)
        .post(s"$nuDesignerHttpAddress/api/migrate")
        .Then()
        .statusCode(200)
        .verifyCommentExists(exampleProcessName.value, "Scenario migrated from DEV by allpermuser")
    }
    "fail when scenario name contains illegal character(s)" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .jsonBody(requestDataWithInvalidScenarioName)
        .post(s"$nuDesignerHttpAddress/api/migrate")
        .Then()
        .statusCode(400)
        .equalsPlainBody(
          "Cannot migrate, following errors occurred: Invalid scenario name #test. Only digits, letters, underscore (_), hyphen (-) and space in the middle are allowed"
        )
    }
    "fail when scenario is archived on target environment" in {
      given()
        .applicationState(
          createArchivedExampleScenario(exampleProcessName, Category1)
        )
        .when()
        .basicAuthAllPermUser()
        .jsonBody(validRequestData)
        .post(s"$nuDesignerHttpAddress/api/migrate")
        .Then()
        .statusCode(400)
        .equalsPlainBody(
          s"Cannot migrate, scenario ${exampleProcessName.value} is archived on test. You have to unarchive scenario on test in order to migrate."
        )
    }
    "fail when validation on source environment has errors" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .jsonBody(invalidRequestData)
        .post(s"$nuDesignerHttpAddress/api/migrate")
        .Then()
        .statusCode(400)
        .equalsPlainBody(
          s"Cannot migrate, following errors occurred: n1 - message"
        )
    }
    "migrate fragment" in {
      given()
        .applicationState(
          createSavedScenario(validFragment, Category1)
        )
        .when()
        .basicAuthAllPermUser()
        .jsonBody(validRequestDataForFragment)
        .post(s"$nuDesignerHttpAddress/api/migrate")
        .Then()
        .statusCode(200)
        .equalsPlainBody("")

    }
  }

  private lazy val sourceEnvironmentId = "DEV"

  private lazy val exampleProcessName = ProcessName("test2")
  private lazy val illegalProcessName = ProcessName("#test")

  private lazy val exampleScenario =
    ScenarioBuilder
      .withCustomMetaData(exampleProcessName.value, Map("environment" -> "test"))
      .source("source", "csv-source-lite")
      .emptySink("sink", "dead-end-lite")

  private lazy val validFragment =
    ScenarioBuilder.fragmentWithInputNodeId("source", "csv-source-lite").emptySink("sink", "dead-end-lite")

  private lazy val exampleGraph = CanonicalProcessConverter.toScenarioGraph(exampleScenario)

  private lazy val successValidationResult = ValidationResult.success

  private lazy val errorValidationResult =
    ValidationResult.errors(
      Map("n1" -> List(NodeValidationError("bad", "message", "", None, NodeValidationErrorType.SaveAllowed))),
      List(),
      List()
    )

  private def prepareRequestJsonData(
      scenarioName: String,
      validationResult: ValidationResult,
      scenarioGraph: ScenarioGraph,
      isFragment: Boolean
  ): String =
    s"""
       |{
       |  "sourceEnvironmentId": "$sourceEnvironmentId",
       |  "processingMode": "Unbounded-Stream",
       |  "engineSetupName": "Flink",
       |  "scenarioToMigrate": {
       |    "name": "${scenarioName}",
       |    "isArchived": false,
       |    "isFragment": $isFragment,
       |    "processingType": "streaming1",
       |    "processCategory": "Category1",
       |    "scenarioGraph": ${scenarioGraph.asJson.noSpaces},
       |    "validationResult": ${validationResult.asJson.noSpaces},
       |    "history": null,
       |    "modelVersion": null
       |  }
       |}
       |""".stripMargin

  private lazy val validRequestData: String =
    prepareRequestJsonData(exampleProcessName.value, successValidationResult, exampleGraph, false)

  private lazy val invalidRequestData: String =
    prepareRequestJsonData(exampleProcessName.value, errorValidationResult, exampleGraph, false)

  private lazy val requestDataWithInvalidScenarioName: String =
    prepareRequestJsonData(illegalProcessName.value, successValidationResult, exampleGraph, false)

  private lazy val validRequestDataForFragment: String =
    prepareRequestJsonData(exampleProcessName.value, successValidationResult, exampleGraph, true)

}
