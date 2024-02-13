package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.given
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers.equalTo
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.test.{NuRestAssureExtensions, NuRestAssureMatchers, PatientScalaFutures, RestAssuredVerboseLogging}
import pl.touk.nussknacker.tests.base.it.{NuItTest2, WithRichConfigScenarioHelper}
import pl.touk.nussknacker.tests.config.WithRichDesignerConfig.TestCategory.Category1
import pl.touk.nussknacker.tests.config.{WithMockableDeploymentManager2, WithRichDesignerConfig}

class NotificationApiHttpServiceSecuritySpec
    extends AnyFreeSpecLike
      with NuItTest2
      with WithRichDesignerConfig
      with WithRichConfigScenarioHelper
      with WithMockableDeploymentManager2
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging
    with PatientScalaFutures {

  "The endpoint for getting notifications when" - {
    "authenticated should" - {
      "return empty list if no notifications are present" in {
        given()
          .basicAuth("admin", "admin")
          .when()
          .get(s"$nuDesignerHttpAddress/api/notifications")
          .Then()
          .statusCode(200)
          .body(
            equalTo("[]")
          )
      }
      "return a list of notifications" in {
        given()
          .basicAuth("admin", "admin")
          .when()
          .get(s"$nuDesignerHttpAddress/api/notifications")
          .Then()
          .statusCode(200)
          .body(
            equalTo("[]")
          )

        val scenarioName = ProcessName("canceled-scenario-01")

        given()
          .applicationState {
            createDeployedCanceledExampleScenario(scenarioName, category = Category1)
          }
          .basicAuth("admin", "admin")
          .when()
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
                 |  "toRefresh": [ "versions", "activity", "state" ]
                 |},
                 |{
                 |   "id": "^\\\\w{8}-\\\\w{4}-\\\\w{4}-\\\\w{4}-\\\\w{12}$$",
                 |   "scenarioName": "$scenarioName",
                 |   "message": "Cancel finished",
                 |   "type": null,
                 |   "toRefresh": [ "versions", "activity", "state" ]
                 |}]""".stripMargin
            )
          )
      }
      "return 405 when invalid HTTP method is passed" in {
        given()
          .when()
          .basicAuth("admin", "admin")
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

    "not authenticated should" - {
      "forbid access" in {
        given()
          .when()
          .basicAuth("unknown-user", "wrong-password")
          .get(s"$nuDesignerHttpAddress/api/notification")
          .Then()
          .statusCode(401)
          .body(equalTo("The supplied authentication is invalid"))
      }
    }
  }

}
