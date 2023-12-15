package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.given
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers.equalTo
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.test.{
  NuRestAssureExtensions,
  NuRestAssureMatchers,
  PatientScalaFutures,
  RestAssuredVerboseLogging
}
import pl.touk.nussknacker.ui.api.helpers.{NuItTest, NuScenarioConfigurationHelper, WithMockableDeploymentManager}

class NotificationApiTest
    extends AnyFreeSpecLike
    with NuItTest
    with WithMockableDeploymentManager
    with NuScenarioConfigurationHelper
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging
    with PatientScalaFutures {

  "The endpoint for getting notifications when" - {
    "authenticated should" - {
      "return empty list if no notifications are present" in {
        given()
          .auth()
          .basic("admin", "admin")
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
          .auth()
          .basic("admin", "admin")
          .when()
          .get(s"$nuDesignerHttpAddress/api/notifications")
          .Then()
          .statusCode(200)
          .body(
            equalTo("[]")
          )

        val deployedProcessName = ProcessName("process-execution-canceled")
        val processId           = createDeployedProcess(deployedProcessName)

        given()
          .auth()
          .basic("admin", "admin")
          .when()
          .get(s"$nuDesignerHttpAddress/api/notifications")
          .Then()
          .statusCode(200)
          .body(
            matchJsonWithRegexValues(
              s"""[{
                   |  "id": "^\\\\w{8}-\\\\w{4}-\\\\w{4}-\\\\w{4}-\\\\w{12}$$",
                   |  "scenarioName": "$deployedProcessName",
                   |  "message": "Deployment finished",
                   |  "type": null,
                   |  "toRefresh": [ "versions", "activity", "state" ]
                   |}]""".stripMargin
            )
          )

        prepareCancel(processId)

        given()
          .auth()
          .basic("admin", "admin")
          .when()
          .get(s"$nuDesignerHttpAddress/api/notifications")
          .Then()
          .statusCode(200)
          .body(
            matchJsonWithRegexValues(
              s"""[{
                 |  "id": "^\\\\w{8}-\\\\w{4}-\\\\w{4}-\\\\w{4}-\\\\w{12}$$",
                 |  "scenarioName": "$deployedProcessName",
                 |  "message": "Deployment finished",
                 |  "type": null,
                 |  "toRefresh": [ "versions", "activity", "state" ]
                 |},
                 |{
                 |   "id": "^\\\\w{8}-\\\\w{4}-\\\\w{4}-\\\\w{4}-\\\\w{12}$$",
                 |   "scenarioName": "$deployedProcessName",
                 |   "message": "Cancel finished",
                 |   "type": null,
                 |   "toRefresh": [ "versions", "activity", "state" ]
                 |}]""".stripMargin
            )
          )
      }
      "return 405 when invalid HTTP method is passed" in {
        given()
          .auth()
          .basic("admin", "admin")
          .when()
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
          .auth()
          .none()
          .when()
          .get(s"$nuDesignerHttpAddress/api/notification")
          .Then()
          .statusCode(401)
          .body(
            equalTo("The resource requires authentication, which was not supplied with the request")
          )
      }
    }
  }

}
