package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.given
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers.equalTo
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.test.{NuRestAssureMatchers, PatientScalaFutures, RestAssuredVerboseLoggingIfValidationFails}
import pl.touk.nussknacker.test.base.it.{NuItTest, WithAccessControlCheckingConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{
  WithAccessControlCheckingConfigRestAssuredUsersExtensions,
  WithAccessControlCheckingDesignerConfig,
  WithMockableDeploymentManager
}
import pl.touk.nussknacker.test.config.WithAccessControlCheckingDesignerConfig.TestCategory.{Category1, Category2}

class NotificationApiHttpServiceSecuritySpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithAccessControlCheckingDesignerConfig
    with WithAccessControlCheckingConfigScenarioHelper
    with WithMockableDeploymentManager
    with WithAccessControlCheckingConfigRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails
    with PatientScalaFutures {

  "The endpoint for getting notifications when" - {
    "authenticated should" - {
      "not see notification related to admin user's actions" in {
        given()
          .applicationState {
            createDeployedCanceledExampleScenario(ProcessName("canceled-scenario-01"), category = Category1)
            createDeployedCanceledExampleScenario(ProcessName("canceled-scenario-02"), category = Category2)
          }
          .when()
          .basicAuthAllPermUser()
          .get(s"$nuDesignerHttpAddress/api/notifications")
          .Then()
          .statusCode(200)
          .body(
            matchJsonWithRegexValues(
              s"""[{
                 |  "id": "^\\\\w{8}-\\\\w{4}-\\\\w{4}-\\\\w{4}-\\\\w{12}$$",
                 |  "scenarioName": null,
                 |  "message": "Configuration reloaded",
                 |  "type": null,
                 |  "toRefresh": [ "creator" ]
                 |}]""".stripMargin
            )
          )
      }
    }
    "not authenticated should" - {
      "forbid access" in {
        given()
          .when()
          .basicAuthUnknownUser()
          .get(s"$nuDesignerHttpAddress/api/notification")
          .Then()
          .statusCode(401)
          .body(equalTo("The supplied authentication is invalid"))
      }
    }
    "no credentials were passed should" - {
      "authenticate as anonymous and return no notifications" in {
        given()
          .applicationState {
            createDeployedCanceledExampleScenario(ProcessName("canceled-scenario-01"), category = Category1)
            createDeployedCanceledExampleScenario(ProcessName("canceled-scenario-02"), category = Category2)
          }
          .when()
          .noAuth()
          .get(s"$nuDesignerHttpAddress/api/notifications")
          .Then()
          .statusCode(200)
          .body(
            matchJsonWithRegexValues(
              s"""[{
                 |  "id": "^\\\\w{8}-\\\\w{4}-\\\\w{4}-\\\\w{4}-\\\\w{12}$$",
                 |  "scenarioName": null,
                 |  "message": "Configuration reloaded",
                 |  "type": null,
                 |  "toRefresh": [ "creator" ]
                 |}]""".stripMargin
            )
          )
      }
    }
  }

}
