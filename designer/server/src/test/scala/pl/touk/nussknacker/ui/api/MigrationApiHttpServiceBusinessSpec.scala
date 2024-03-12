package pl.touk.nussknacker.ui.api

import io.circe.syntax._
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import io.restassured.response.ValidatableResponse
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

class MigrationApiHttpServiceBusinessSpec
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
    "migrate scenario and add update comment when scenario does not exist on target environment" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .jsonBody(validRequestData)
        .post(s"$nuDesignerHttpAddress/api/migrate")
        .Then()
        .statusCode(200)
        .verifyCommentExists(exampleProcessName.value, "Scenario migrated from DEV by allpermuser")
        .verifyScenarioAfterMigration(
          exampleProcessName.value,
          processVersionId = 2,
          isFragment = false,
          modifiedBy = "Remote[allpermuser]",
          createdBy = "Remote[allpermuser]",
          modelVersion = 0,
          history = history,
          scenarioGraph = scenarioGraph
        )
    }
    "migrate scenario and add update comment when scenario exists on target environment" in {
      given()
        .applicationState(
          createSavedScenario(exampleScenario, Category1)
        )
        .when()
        .basicAuthAllPermUser()
        .jsonBody(validRequestData2)
        .post(s"$nuDesignerHttpAddress/api/migrate")
        .Then()
        .statusCode(200)
        .verifyCommentExists(exampleProcessName.value, "Scenario migrated from DEV by allpermuser")
        .verifyScenarioAfterMigration(
          exampleProcessName.value,
          processVersionId = 2,
          isFragment = false,
          modifiedBy = "Remote[allpermuser]",
          createdBy = "admin",
          modelVersion = 0,
          history = history,
          scenarioGraph = scenarioGraphAfterMigration
        )
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
    "migrate fragment and add update comment when fragment exists in target environment" in {
      given()
        .applicationState(
          createSavedFragment(validFragment, Category1)
        )
        .when()
        .basicAuthAllPermUser()
        .jsonBody(validRequestDataForFragment2)
        .post(s"$nuDesignerHttpAddress/api/migrate")
        .Then()
        .statusCode(200)
        .verifyCommentExists(validFragment.name.value, "Scenario migrated from DEV by allpermuser")
        .verifyScenarioAfterMigration(
          validFragment.name.value,
          processVersionId = 2,
          isFragment = true,
          modifiedBy = "Remote[allpermuser]",
          createdBy = "admin",
          modelVersion = 0,
          history = history,
          scenarioGraph = fragmentGraphAfterMigration
        )
    }
    "migrate fragment and add update comment when fragment does not exist in target environment" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .jsonBody(validRequestDataForFragment)
        .post(s"$nuDesignerHttpAddress/api/migrate")
        .Then()
        .statusCode(200)
        .verifyCommentExists(validFragment.name.value, "Scenario migrated from DEV by allpermuser")
        .verifyScenarioAfterMigration(
          validFragment.name.value,
          processVersionId = 2,
          isFragment = true,
          modifiedBy = "Remote[allpermuser]",
          createdBy = "Remote[allpermuser]",
          modelVersion = 0,
          history = history,
          scenarioGraph = fragmentGraph
        )
    }
  }

  private lazy val sourceEnvironmentId = "DEV"

  private lazy val exampleProcessName = ProcessName("test2")
  private lazy val illegalProcessName = ProcessName("#test")

  private lazy val history =
    s"""
      | [
      |    {
      |      "processVersionId": "${regexes.digitsRegex}",
      |      "createDate": "${regexes.zuluDateRegex}",
      |      "user": "${regexes.any}",
      |      "modelVersion": "${regexes.digitsRegex}",
      |      "actions": []
      |    },
      |    {
      |      "processVersionId": "${regexes.digitsRegex}",
      |      "createDate": "${regexes.zuluDateRegex}",
      |      "user": "${regexes.any}",
      |      "modelVersion": "${regexes.digitsRegex}",
      |      "actions": []
      |    }
      |  ]
      |""".stripMargin

  private lazy val scenarioGraph =
    s"""
       |{
       |  "properties": {
       |    "additionalFields": {
       |      "description": null,
       |      "properties": {
       |        "environment": "test"
       |      },
       |      "metaDataType": "CustomMetadata"
       |    }
       |  },
       |  "nodes": [
       |    {
       |      "id": "source",
       |      "ref": {
       |        "typ": "csv-source-lite",
       |        "parameters": []
       |      },
       |      "additionalFields": null,
       |      "type": "Source"
       |    },
       |    {
       |      "id": "sink",
       |      "ref": {
       |        "typ": "dead-end-lite",
       |        "parameters": []
       |      },
       |      "endResult": null,
       |      "isDisabled": null,
       |      "additionalFields": null,
       |      "type": "Sink"
       |    }
       |  ],
       |  "edges": [
       |    {
       |      "from": "source",
       |      "to": "sink",
       |      "edgeType": null
       |    }
       |  ]
       |}
""".stripMargin

  private lazy val scenarioGraphAfterMigration =
    s"""
       |{
       |  "properties": {
       |    "additionalFields": {
       |      "description": null,
       |      "properties": {
       |        "environment": "test"
       |      },
       |      "metaDataType": "CustomMetadata"
       |    }
       |  },
       |  "nodes": [
       |    {
       |      "id": "source2",
       |      "ref": {
       |        "typ": "csv-source-lite",
       |        "parameters": []
       |      },
       |      "additionalFields": null,
       |      "type": "Source"
       |    },
       |    {
       |      "id": "sink2",
       |      "ref": {
       |        "typ": "dead-end-lite",
       |        "parameters": []
       |      },
       |      "endResult": null,
       |      "isDisabled": null,
       |      "additionalFields": null,
       |      "type": "Sink"
       |    }
       |  ],
       |  "edges": [
       |    {
       |      "from": "source2",
       |      "to": "sink2",
       |      "edgeType": null
       |    }
       |  ]
       |}
""".stripMargin

  private lazy val fragmentGraph =
    s"""
       |{
       |  "properties": {
       |    "additionalFields": {
       |      "description": null,
       |      "properties": {
       |        "docsUrl": ""
       |      },
       |      "metaDataType": "FragmentSpecificData"
       |    }
       |  },
       |  "nodes": [
       |    {
       |      "id": "csv-source-lite",
       |      "parameters": [],
       |      "additionalFields": null,
       |      "type": "FragmentInputDefinition"
       |    },
       |    {
       |      "id": "sink",
       |      "ref": {
       |        "typ": "dead-end-lite",
       |        "parameters": []
       |      },
       |      "endResult": null,
       |      "isDisabled": null,
       |      "additionalFields": null,
       |      "type": "Sink"
       |    }
       |  ],
       |  "edges": [
       |    {
       |      "from": "csv-source-lite",
       |      "to": "sink",
       |      "edgeType": null
       |    }
       |  ]
       |}
""".stripMargin

  private lazy val fragmentGraphAfterMigration =
    s"""
       |{
       |  "properties": {
       |    "additionalFields": {
       |      "description": null,
       |      "properties": {
       |        "docsUrl": ""
       |      },
       |      "metaDataType": "FragmentSpecificData"
       |    }
       |  },
       |  "nodes": [
       |    {
       |      "id": "csv-source-lite",
       |      "parameters": [],
       |      "additionalFields": null,
       |      "type": "FragmentInputDefinition"
       |    },
       |    {
       |      "id": "sink2",
       |      "ref": {
       |        "typ": "dead-end-lite",
       |        "parameters": []
       |      },
       |      "endResult": null,
       |      "isDisabled": null,
       |      "additionalFields": null,
       |      "type": "Sink"
       |    }
       |  ],
       |  "edges": [
       |    {
       |      "from": "csv-source-lite",
       |      "to": "sink2",
       |      "edgeType": null
       |    }
       |  ]
       |}
""".stripMargin

  private lazy val exampleScenario =
    ScenarioBuilder
      .withCustomMetaData(exampleProcessName.value, Map("environment" -> "test"))
      .source("source", "csv-source-lite")
      .emptySink("sink", "dead-end-lite")

  private lazy val exampleScenarioV2 =
    ScenarioBuilder
      .withCustomMetaData(exampleProcessName.value, Map("environment" -> "test"))
      .source("source2", "csv-source-lite")
      .emptySink("sink2", "dead-end-lite")

  private lazy val validFragment =
    ScenarioBuilder.fragmentWithInputNodeId("source", "csv-source-lite").emptySink("sink", "dead-end-lite")

  private lazy val validFragmentV2 =
    ScenarioBuilder.fragmentWithInputNodeId("source2", "csv-source-lite").emptySink("sink2", "dead-end-lite")

  private lazy val exampleGraph = CanonicalProcessConverter.toScenarioGraph(exampleScenario)

  private lazy val exampleGraphV2 = CanonicalProcessConverter.toScenarioGraph(exampleScenarioV2)

  private lazy val exampleFragmentGraph = CanonicalProcessConverter.toScenarioGraph(validFragment)

  private lazy val exampleFragmentGraphV2 = CanonicalProcessConverter.toScenarioGraph(validFragmentV2)

  private def prepareRequestJsonData(
      scenarioName: String,
      scenarioGraph: ScenarioGraph,
      isFragment: Boolean
  ): String =
    s"""
       |{
       |  "sourceEnvironmentId": "$sourceEnvironmentId",
       |  "processingMode": "Unbounded-Stream",
       |  "engineSetupName": "Flink",
       |  "processName": "${scenarioName}",
       |  "isFragment": $isFragment,
       |  "processingType": "streaming1",
       |  "processCategory": "Category1",
       |  "scenarioGraph": ${scenarioGraph.asJson.noSpaces}
       |}
       |""".stripMargin

  private lazy val validRequestData: String =
    prepareRequestJsonData(exampleProcessName.value, exampleGraph, false)

  private lazy val validRequestData2: String =
    prepareRequestJsonData(exampleProcessName.value, exampleGraphV2, false)

  private lazy val requestDataWithInvalidScenarioName: String =
    prepareRequestJsonData(illegalProcessName.value, exampleGraph, false)

  private lazy val validRequestDataForFragment: String =
    prepareRequestJsonData(validFragment.name.value, exampleFragmentGraph, true)

  private lazy val validRequestDataForFragment2: String =
    prepareRequestJsonData(validFragment.name.value, exampleFragmentGraphV2, true)

  implicit class ExtractScenario[T <: ValidatableResponse](validatableResponse: T) {

    def verifyScenarioAfterMigration(
        scenarioName: String,
        processVersionId: Int,
        isFragment: Boolean,
        modifiedBy: String,
        createdBy: String,
        modelVersion: Int,
        history: String,
        scenarioGraph: String
    ): ValidatableResponse =
      given()
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/processes/$scenarioName/basic")
        .Then()
        .statusCode(200)
        .body(
          matchJsonWithRegexValues(
            s"""
               |{
               |  "name": "$scenarioName",
               |  "processId": null,
               |  "processVersionId": $processVersionId,
               |  "isLatestVersion": true,
               |  "description": null,
               |  "isArchived": false,
               |  "isFragment": $isFragment,
               |  "processingType": "streaming1",
               |  "processCategory": "Category1",
               |  "processingMode": "Unbounded-Stream",
               |  "engineSetupName": "Mockable",
               |  "modificationDate": "${regexes.zuluDateRegex}",
               |  "modifiedAt": "${regexes.zuluDateRegex}",
               |  "modifiedBy": "$modifiedBy",
               |  "createdAt": "${regexes.zuluDateRegex}",
               |  "createdBy": "$createdBy",
               |  "tags": [],
               |  "lastDeployedAction": null,
               |  "lastStateAction": null,
               |  "lastAction": null,
               |  "scenarioGraph": $scenarioGraph,
               |  "validationResult": null,
               |  "history": $history,
               |  "modelVersion": $modelVersion,
               |  "state": null
               |}
               |""".stripMargin
          )
        )

  }

}
