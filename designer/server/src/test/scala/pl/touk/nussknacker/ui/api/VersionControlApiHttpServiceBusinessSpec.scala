package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.concurrent.Eventually
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.test.{
  NuRestAssureExtensions,
  NuRestAssureMatchers,
  RestAssuredVerboseLoggingIfValidationFails,
  StandardPatientScalaFutures
}
import pl.touk.nussknacker.test.base.it.{NuItTest, WithCategoryUsedMoreThanOnceConfigScenarioHelper}
import pl.touk.nussknacker.test.config.WithCategoryUsedMoreThanOnceDesignerConfig
import pl.touk.nussknacker.test.processes.WithScenarioActivitySpecAsserts

class VersionControlApiHttpServiceBusinessSpec
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

  "The endpoint for version control should" - {
    "return the latest version when the scenario is unchanged" in {
      given()
        .applicationState(
          createSavedScenario(exampleScenario)
        )
        .when()
        .basicAuthAllPermUser()
        .jsonBody(prepareRequestData(localVersion = 1))
        .post(s"$nuDesignerHttpAddress/api/versionControl/test/versionValidation")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          prepareResponseData(
            processName = processName.value,
            isLatest = true,
            localVersion = 1,
            latestDeployedVersion = None,
            latestVersion = 1,
            modifiedBy = "admin"
          )
        )
    }

    "return an updated latest version when the scenario has changed" in {
      given()
        .applicationState(
          {
            createSavedScenario(exampleScenario)
            updateScenario(processName, updatedScenario)
          }
        )
        .when()
        .basicAuthAllPermUser()
        .jsonBody(prepareRequestData(localVersion = 1))
        .post(s"$nuDesignerHttpAddress/api/versionControl/test/versionValidation")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          prepareResponseData(
            processName = processName.value,
            isLatest = false,
            localVersion = 1,
            latestDeployedVersion = None,
            latestVersion = 2,
            modifiedBy = "admin"
          )
        )
    }

    "return an updated latest deployed version" in {
      given()
        .applicationState(
          {
            val pid = createSavedScenario(exampleScenario)
            prepareDeploy(pid)
            updateScenario(processName, updatedScenario)
          }
        )
        .when()
        .basicAuthAllPermUser()
        .jsonBody(prepareRequestData(localVersion = 1))
        .post(s"$nuDesignerHttpAddress/api/versionControl/test/versionValidation")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          prepareResponseData(
            processName = processName.value,
            isLatest = false,
            localVersion = 1,
            latestDeployedVersion = Some(1),
            latestVersion = 2,
            modifiedBy = "admin"
          )
        )
    }

    "return an error when the scenario process ID is missing" in {
      given()
        .applicationState(
          createSavedScenario(exampleScenario)
        )
        .when()
        .basicAuthAllPermUser()
        .jsonBody(prepareRequestData(localVersion = 2))
        .post(s"$nuDesignerHttpAddress/api/versionControl/${missingProcessName.value}/versionValidation")
        .Then()
        .statusCode(400)
        .equalsPlainBody(s"Missing process id for scenario with name: ${missingProcessName.value}")
    }
  }

  private lazy val exampleScenario =
    ScenarioBuilder
      .withCustomMetaData("test", Map("environment" -> "test"))
      .source("source", "csv-source-lite")
      .emptySink("sink", "dead-end-lite")

  private lazy val updatedScenario =
    ScenarioBuilder
      .withCustomMetaData("test", Map("environment" -> "test"))
      .source("source", "csv-source-lite-v2")
      .emptySink("sink", "dead-end-lite-v2")

  private lazy val processName        = ProcessName("test")
  private lazy val missingProcessName = ProcessName("missing-test")

  private def prepareRequestData(localVersion: Int) =
    s"""
      |{
      |  "localVersion": $localVersion
      |}
      |""".stripMargin

  private def prepareResponseData(
      processName: String,
      isLatest: Boolean,
      localVersion: Int,
      latestDeployedVersion: Option[Long],
      latestVersion: Int,
      modifiedBy: String
  ) =
    s"""
      |{
      |  "processName": "$processName",
      |  "isLatest": $isLatest,
      |  "localVersion": $localVersion,
      |  "latestDeployedVersion": ${latestDeployedVersion.getOrElse("null")},
      |  "latestVersion": $latestVersion,
      |  "modifiedBy": "$modifiedBy"
      |}
      |""".stripMargin

}
