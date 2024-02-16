package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.given
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers.equalTo
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.test.base.it.{NuItTest, WithRichConfigScenarioHelper}
import pl.touk.nussknacker.test.config.WithRichDesignerConfig.TestCategory.{Category1, Category2}
import pl.touk.nussknacker.test.config.{
  WithMockableDeploymentManager,
  WithRichConfigRestAssuredUsersExtensions,
  WithRichDesignerConfig
}
import pl.touk.nussknacker.test.{NuRestAssureMatchers, PatientScalaFutures, RestAssuredVerboseLogging}

class NotificationApiHttpServiceSecuritySpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithRichDesignerConfig
    with WithRichConfigScenarioHelper
    with WithMockableDeploymentManager
    with WithRichConfigRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging
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
          .equalsJsonBody("[]")
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
          .equalsJsonBody("[]")
      }
    }
  }

}
