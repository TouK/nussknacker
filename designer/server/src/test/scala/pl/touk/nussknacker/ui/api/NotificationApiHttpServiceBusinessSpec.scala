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
    // We can't easily recognize if configuration was changed between restarts so just in case we send this notification
    "return initial notification about configuration reload just after start of application" in {
      given()
        .when()
        .basicAuthAdmin()
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
    "return notification when processing type data are reloaded" in {
      given()
        .when()
        .applicationState {
          reloadConfiguration()
        }
        .basicAuthAdmin()
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
               |},
               |{
               |  "id": "^\\\\w{8}-\\\\w{4}-\\\\w{4}-\\\\w{4}-\\\\w{12}$$",
               |  "scenarioName": null,
               |  "message": "Configuration reloaded",
               |  "type": null,
               |  "toRefresh": [ "creator" ]
               |}]""".stripMargin
          )
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
               |},
               |{
               |  "id": "^\\\\w{8}-\\\\w{4}-\\\\w{4}-\\\\w{4}-\\\\w{12}$$",
               |  "scenarioName": null,
               |  "message": "Configuration reloaded",
               |  "type": null,
               |  "toRefresh": [ "creator" ]
               |},
               |{
               |  "id": "^\\\\w{8}-\\\\w{4}-\\\\w{4}-\\\\w{4}-\\\\w{12}$$",
               |  "scenarioName": null,
               |  "message": "Configuration reloaded",
               |  "type": null,
               |  "toRefresh": [ "creator" ]
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

  private def reloadConfiguration(): Unit = {
    given()
      .when()
      .basicAuthAdmin()
      .post(s"$nuDesignerHttpAddress/api/app/processingtype/reload")
      .Then()
      .statusCode(204)
  }

}
