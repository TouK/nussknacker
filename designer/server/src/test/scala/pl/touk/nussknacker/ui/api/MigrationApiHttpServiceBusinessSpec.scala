package pl.touk.nussknacker.ui.api

import io.circe.syntax._
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import io.restassured.response.ValidatableResponse
import org.hamcrest.Matchers._
import org.scalatest.concurrent.Eventually
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.test.base.it.{NuItTest, WithCategoryUsedMoreThanOnceConfigScenarioHelper}
import pl.touk.nussknacker.test.config.WithAccessControlCheckingDesignerConfig.TestCategory.Category1
import pl.touk.nussknacker.test.config.WithCategoryUsedMoreThanOnceDesignerConfig
import pl.touk.nussknacker.test.processes.WithScenarioActivitySpecAsserts
import pl.touk.nussknacker.test.{
  NuRestAssureExtensions,
  NuRestAssureMatchers,
  RestAssuredVerboseLoggingIfValidationFails,
  StandardPatientScalaFutures
}
import pl.touk.nussknacker.ui.migrations.{MigrateScenarioData, MigrationApiAdapters}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.util.ApiAdapter

// FIXME: For migrating between different API version should be written end to end test (e2e-tests directory)
class MigrationApiHttpServiceBusinessSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithCategoryUsedMoreThanOnceDesignerConfig
    with WithScenarioActivitySpecAsserts
    with WithCategoryUsedMoreThanOnceConfigScenarioHelper
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails
    with Eventually
    with StandardPatientScalaFutures {

  val adapters: Map[Int, ApiAdapter[MigrateScenarioData]] = MigrationApiAdapters.adapters

  "The endpoint for migration api version should" - {
    "return current api version" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/migration/scenario/description/version")
        .Then()
        .statusCode(200)
        .body(
          "version",
          equalTo[Int](1)
        )
    }
  }

  "The endpoint for scenario migration between environments should" - {
    "migrate scenario and add update comment when scenario does not exist on target environment" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .jsonBody(validRequestData)
        .post(s"$nuDesignerHttpAddress/api/migrate")
        .Then()
        .statusCode(200)
        .verifyApplicationState {
          eventually {
            verifyCommentExists(exampleProcessName.value, "Scenario migrated from DEV by remoteUser", "allpermuser")
            verifyScenarioAfterMigration(
              exampleProcessName.value,
              processVersionId = 2,
              isFragment = false,
              modifiedBy = "remoteUser",
              createdBy = "remoteUser",
              modelVersion = 0,
              historyProcessVersions = List(1, 2),
              scenarioGraphNodeIds = List("sink", "source")
            )
          }
        }
    }
    "migrate scenario and add update comment when scenario exists on target environment" in {
      given()
        .applicationState(
          createSavedScenario(exampleScenario)
        )
        .when()
        .basicAuthAllPermUser()
        .jsonBody(validRequestDataV2)
        .post(s"$nuDesignerHttpAddress/api/migrate")
        .Then()
        .statusCode(200)
        .verifyApplicationState {
          eventually {
            verifyCommentExists(exampleProcessName.value, "Scenario migrated from DEV by remoteUser", "allpermuser")
            verifyScenarioAfterMigration(
              scenarioName = exampleProcessName.value,
              processVersionId = 2,
              isFragment = false,
              modifiedBy = "remoteUser",
              createdBy = "admin",
              modelVersion = 0,
              historyProcessVersions = List(1, 2),
              scenarioGraphNodeIds = List("sink2", "source2")
            )
          }
        }
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
          createArchivedExampleScenario(exampleProcessName)
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
          createSavedFragment(validFragment)
        )
        .when()
        .basicAuthAllPermUser()
        .jsonBody(validRequestDataForFragmentV2)
        .post(s"$nuDesignerHttpAddress/api/migrate")
        .Then()
        .statusCode(200)
        .verifyApplicationState {
          eventually {
            verifyCommentExists(validFragment.name.value, "Scenario migrated from DEV by remoteUser", "allpermuser")
            verifyScenarioAfterMigration(
              validFragment.name.value,
              processVersionId = 2,
              isFragment = true,
              modifiedBy = "remoteUser",
              createdBy = "admin",
              modelVersion = 0,
              historyProcessVersions = List(1, 2),
              scenarioGraphNodeIds = List("sink2", "csv-source-lite")
            )
          }
        }
    }
    "migrate fragment and add update comment when fragment does not exist in target environment" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .jsonBody(validRequestDataForFragment)
        .post(s"$nuDesignerHttpAddress/api/migrate")
        .Then()
        .statusCode(200)
        .verifyApplicationState {
          eventually {
            verifyCommentExists(validFragment.name.value, "Scenario migrated from DEV by remoteUser", "allpermuser")
            verifyScenarioAfterMigration(
              validFragment.name.value,
              processVersionId = 2,
              isFragment = true,
              modifiedBy = "remoteUser",
              createdBy = "remoteUser",
              modelVersion = 0,
              historyProcessVersions = List(1, 2),
              scenarioGraphNodeIds = List("sink", "csv-source-lite")
            )
          }
        }
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
       |  "version": "1",
       |  "sourceEnvironmentId": "$sourceEnvironmentId",
       |  "remoteUserName": "remoteUser",
       |  "processingMode": "Unbounded-Stream",
       |  "engineSetupName": "Mockable",
       |  "processName": "$scenarioName",
       |  "isFragment": $isFragment,
       |  "processCategory": "${Category1.stringify}",
       |  "scenarioGraph": ${scenarioGraph.asJson.noSpaces}
       |}
       |""".stripMargin

  private lazy val validRequestData: String =
    prepareRequestJsonData(exampleProcessName.value, exampleGraph, isFragment = false)

  private lazy val validRequestDataV2: String =
    prepareRequestJsonData(exampleProcessName.value, exampleGraphV2, isFragment = false)

  private lazy val requestDataWithInvalidScenarioName: String =
    prepareRequestJsonData(illegalProcessName.value, exampleGraph, isFragment = false)

  private lazy val validRequestDataForFragment: String =
    prepareRequestJsonData(validFragment.name.value, exampleFragmentGraph, isFragment = true)

  private lazy val validRequestDataForFragmentV2: String =
    prepareRequestJsonData(validFragment.name.value, exampleFragmentGraphV2, isFragment = true)

  private def verifyScenarioAfterMigration(
      scenarioName: String,
      processVersionId: Int,
      isFragment: Boolean,
      modifiedBy: String,
      createdBy: String,
      modelVersion: Int,
      historyProcessVersions: List[Int],
      scenarioGraphNodeIds: List[String]
  ): ValidatableResponse =
    given()
      .when()
      .basicAuthAllPermUser()
      .get(
        s"$nuDesignerHttpAddress/api/processes/$scenarioName?skipValidateAndResolve=true&skipNodeResults=true"
      )
      .Then()
      .body(
        "name",
        equalTo(scenarioName),
        "processVersionId",
        equalTo(processVersionId),
        "isFragment",
        equalTo(isFragment),
        "modifiedBy",
        equalTo(modifiedBy),
        "createdBy",
        equalTo(createdBy),
        "history.processVersionId",
        containsInAnyOrder(historyProcessVersions: _*),
        "scenarioGraph.nodes.id",
        containsInAnyOrder(scenarioGraphNodeIds: _*),
        "modelVersion",
        equalTo(modelVersion)
      )

}
