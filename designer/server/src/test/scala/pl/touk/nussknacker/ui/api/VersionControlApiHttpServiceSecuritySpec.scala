package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers.equalTo
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.test.{NuRestAssureMatchers, RestAssuredVerboseLoggingIfValidationFails}
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{WithBusinessCaseRestAssuredUsersExtensions, WithSimplifiedDesignerConfig}

class VersionControlApiHttpServiceSecuritySpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithSimplifiedConfigScenarioHelper
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails {

  "The endpoint for version control should" - {
    "authenticated should" - {
      "return the latest version" in {
        given()
          .applicationState(
            createSavedScenario(exampleScenario)
          )
          .when()
          .basicAuthAllPermUser()
          .jsonBody(requestDto)
          .post(s"$nuDesignerHttpAddress/api/versionControl/${processName.value}/versionValidation")
          .Then()
          .statusCode(200)
      }
    }
    "non authenticated should" - {
      "forbid access" in {
        given()
          .applicationState(
            createSavedScenario(exampleScenario)
          )
          .when()
          .basicAuthUnknownUser()
          .jsonBody(requestDto)
          .post(s"$nuDesignerHttpAddress/api/versionControl/${processName.value}/versionValidation")
          .Then()
          .statusCode(401)
          .body(equalTo("The supplied authentication is invalid"))
      }
    }
    "no credentials were passed should" - {
      "forbid access" in {
        given()
          .applicationState(
            createSavedScenario(exampleScenario)
          )
          .when()
          .noAuth()
          .jsonBody(requestDto)
          .post(s"$nuDesignerHttpAddress/api/versionControl/${processName.value}/versionValidation")
          .Then()
          .statusCode(401)
          .body(equalTo("Invalid value (missing)"))
      }
    }
  }

  private lazy val processName = ProcessName("test")

  private lazy val exampleScenario =
    ScenarioBuilder
      .withCustomMetaData("test", Map("environment" -> "test"))
      .source("source", "csv-source-lite")
      .emptySink("sink", "dead-end-lite")

  private lazy val requestDto =
    """
      |{
      |  "localVersion": 1
      |}
      |""".stripMargin

}
