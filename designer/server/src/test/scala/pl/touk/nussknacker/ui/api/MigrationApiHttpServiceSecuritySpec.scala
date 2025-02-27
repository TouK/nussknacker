package pl.touk.nussknacker.ui.api

import io.circe.syntax._
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.test.{NuRestAssureMatchers, RestAssuredVerboseLoggingIfValidationFails}
import pl.touk.nussknacker.test.base.it.{NuItTest, WithAccessControlCheckingConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{
  WithAccessControlCheckingConfigRestAssuredUsersExtensions,
  WithAccessControlCheckingDesignerConfig,
  WithMockableDeploymentManager
}
import pl.touk.nussknacker.test.config.WithAccessControlCheckingDesignerConfig.TestCategory
import pl.touk.nussknacker.test.config.WithAccessControlCheckingDesignerConfig.TestCategory.{Category1, Category2}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter

class MigrationApiHttpServiceSecuritySpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithAccessControlCheckingDesignerConfig
    with WithAccessControlCheckingConfigScenarioHelper
    with WithAccessControlCheckingConfigRestAssuredUsersExtensions
    with WithMockableDeploymentManager
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails {

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
            createSavedScenario(exampleScenario, Category2)
          )
          .when()
          .basicAuthLimitedReader()
          .jsonBody(requestData)
          .post(s"$nuDesignerHttpAddress/api/migrate")
          .Then()
          .statusCode(401)
          .equalsPlainBody("The supplied user [limitedReader] is not authorized to access this resource")
      }
      "forbid access for user with limited writing permissions" in {
        given()
          .applicationState(
            createSavedScenario(exampleScenario, Category2)
          )
          .when()
          .basicAuthLimitedWriter()
          .jsonBody(prepareRequestData(exampleProcessName.value, Category2))
          .post(s"$nuDesignerHttpAddress/api/migrate")
          .Then()
          .statusCode(401)
          .equalsPlainBody("The supplied user [limitedWriter] is not authorized to access this resource")
      }
    }
    "no credentials were passed should" - {
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
    "impersonating user has permission to impersonate should" - {
      "allow migration for impersonated user with appropriate permissions" in {
        given()
          .applicationState(
            createSavedScenario(exampleScenario, Category1)
          )
          .when()
          .basicAuthAllPermUser()
          .impersonateWriterUser()
          .jsonBody(requestData)
          .post(s"$nuDesignerHttpAddress/api/migrate")
          .Then()
          .statusCode(200)
          .equalsPlainBody("")
      }
      "forbid access for impersonated user with limited reading permissions" in {
        given()
          .applicationState(
            createSavedScenario(exampleScenario, Category1)
          )
          .when()
          .basicAuthAllPermUser()
          .impersonateReaderUser()
          .jsonBody(requestData)
          .post(s"$nuDesignerHttpAddress/api/migrate")
          .Then()
          .statusCode(401)
          .equalsPlainBody("The supplied user [reader] is not authorized to access this resource")
      }
      "forbid admin impersonation with default configuration" in {
        given()
          .applicationState(
            createSavedScenario(exampleScenario, Category1)
          )
          .when()
          .basicAuthAllPermUser()
          .impersonateAdminUser()
          .jsonBody(requestData)
          .post(s"$nuDesignerHttpAddress/api/migrate")
          .Then()
          .statusCode(403)
          .equalsPlainBody("The supplied authentication is not authorized to impersonate")
      }
    }
    "impersonating user does not have permission to impersonate should" - {
      "forbid access" in {
        given()
          .applicationState(
            createSavedScenario(exampleScenario, Category1)
          )
          .when()
          .basicAuthWriter()
          .impersonateWriterUser()
          .jsonBody(requestData)
          .post(s"$nuDesignerHttpAddress/api/migrate")
          .Then()
          .statusCode(403)
          .equalsPlainBody("The supplied authentication is not authorized to impersonate")
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

  private def prepareRequestData(scenarioName: String, processCategory: TestCategory): String =
    s"""
       |{
       |  "version": "2",
       |  "sourceEnvironmentId": "$sourceEnvironmentId",
       |  "remoteUserName": "remoteUser",
       |  "processingMode": "Unbounded-Stream",
       |  "engineSetupName": "Mockable",
       |  "processName": "$scenarioName",
       |  "isFragment": false,
       |  "processCategory": "${processCategory.stringify}",
       |  "scenarioLabels": ["tag1", "tag2"],
       |  "scenarioGraph": ${exampleGraph.asJson.noSpaces}
       |}
       |""".stripMargin

  private lazy val requestData: String = prepareRequestData(exampleProcessName.value, processCategory = Category1)

}
