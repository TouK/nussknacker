package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.given
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers.equalTo
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{
  WithBusinessCaseRestAssuredUsersExtensions,
  WithMockableDeploymentManager,
  WithSimplifiedDesignerConfig
}
import pl.touk.nussknacker.test.{NuRestAssureMatchers, PatientScalaFutures, RestAssuredVerboseLoggingIfValidationFails}

class NotificationApiHttpServiceBusinessSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithSimplifiedConfigScenarioHelper
    with WithMockableDeploymentManager
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails
    with PatientScalaFutures {

  "The endpoint for getting notifications should" - {
    "return empty list if no notifications are present" in {
      given()
        .when()
        .basicAuthAdmin()
        .get(s"$nuDesignerHttpAddress/api/notifications")
        .Then()
        .statusCode(200)
        .body(
          equalTo("[]")
        )
    }
    "return a list of notifications" in {
      val scenarioName = ProcessName("canceled-scenario-01")
      given()
        .applicationState {
          createDeployedCanceledExampleScenario(scenarioName)
        }
        .when()
        .basicAuthAdmin()
        .get(s"$nuDesignerHttpAddress/api/notifications")
        .Then()
        .statusCode(200)
        .body(
          matchJsonWithRegexValues(
            s"""[{
               |  "id": "^\\\\w{8}-\\\\w{4}-\\\\w{4}-\\\\w{4}-\\\\w{12}$$",
               |  "scenarioName": "$scenarioName",
               |  "message": "Deployment finished",
               |  "type": null,
               |  "toRefresh": [ "activity", "state" ]
               |},
               |{
               |   "id": "^\\\\w{8}-\\\\w{4}-\\\\w{4}-\\\\w{4}-\\\\w{12}$$",
               |   "scenarioName": "$scenarioName",
               |   "message": "Cancel finished",
               |   "type": null,
               |   "toRefresh": [ "activity", "state" ]
               |}]""".stripMargin
          )
        )
    }
    "return 405 when an invalid HTTP method is passed" in {
      given()
        .when()
        .basicAuthAdmin()
        .put(s"$nuDesignerHttpAddress/api/notifications")
        .Then()
        .statusCode(405)
        .body(
          equalTo(
            s"Method Not Allowed"
          )
        )
    }
  }

}
