package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{
  WithMockableDeploymentManager,
  WithSimplifiedConfigRestAssuredUsersExtensions,
  WithSimplifiedDesignerConfig
}
import pl.touk.nussknacker.test.{NuRestAssureExtensions, NuRestAssureMatchers, RestAssuredVerboseLogging}

import java.util.UUID

class ManagementApiHttpServiceSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithSimplifiedConfigScenarioHelper
    with WithSimplifiedConfigRestAssuredUsersExtensions
    with WithMockableDeploymentManager
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging {

  private lazy val exampleScenarioName = UUID.randomUUID().toString

  private lazy val exampleScenario = ScenarioBuilder
    .streaming(exampleScenarioName)
    .source("sourceId", "barSource")
    .emptySink("sinkId", "barSink")

  "The endpoint for nodes validation should " - {
    "validate proper request without errors and " - {
      "return valid for valid request with empty params " in {
        given()
          .applicationState {
            createDeployedScenario(exampleScenario)
          }
          .when()
          .basicAuthAllPermUser()
          .jsonBody(
            s"""{
             | "actionName": "hello",
             | "params": {}
             |}""".stripMargin
          )
          .post(s"$nuDesignerHttpAddress/api/processManagement/customAction/$exampleScenarioName/validation")
          .Then()
          .statusCode(200)
          .equalsJsonBody(
            s"""{
               |    "validationErrors": [
               |
               |    ],
               |    "validationPerformed": true
               |}""".stripMargin
          )
      }

      "return valid for valid request with non empty params " in {
        given()
          .applicationState {
            createDeployedScenario(exampleScenario)
          }
          .when()
          .basicAuthAllPermUser()
          .jsonBody(
            s"""{
               | "actionName": "some-params-action",
               | "params": {
               |    "param1": "myValidParam"
               |  }
               |}""".stripMargin
          )
          .post(s"$nuDesignerHttpAddress/api/processManagement/customAction/$exampleScenarioName/validation")
          .Then()
          .statusCode(200)
          .equalsJsonBody(
            s"""{
               |    "validationErrors": [
               |
               |    ],
               |    "validationPerformed": true
               |}""".stripMargin
          )
      }
      "return invalid for invalid data" in {
        given()
          .applicationState {
            createDeployedScenario(exampleScenario)
          }
          .when()
          .basicAuthAllPermUser()
          .jsonBody(
            s"""{
             |  "actionName": "some-params-action",
             |  "params": {
             |    "param1": ""
             |  }
             |}
             |""".stripMargin
          )
          .post(s"$nuDesignerHttpAddress/api/processManagement/customAction/$exampleScenarioName/validation")
          .Then()
          .statusCode(200)
          .equalsJsonBody(
            s"""{
               |    "validationErrors": [
               |        {
               |            "typ": "BlankParameter",
               |            "message": "This field value is required and can not be blank",
               |            "description": "Please fill field value for this parameter",
               |            "fieldName": "param1",
               |            "errorType": "SaveAllowed",
               |            "details": null
               |        }
               |    ],
               |    "validationPerformed": true
               |}""".stripMargin
          )
      }
      "return invalid for wrong request params" in {
        given()
          .applicationState {
            createDeployedScenario(exampleScenario)
          }
          .when()
          .basicAuthAllPermUser()
          .jsonBody(
            s"""{
               |  "actionName": "hello",
               |  "params": {
               |    "property1": "abc",
               |    "property2": "xyz"
               |  }
               |}
               |""".stripMargin
          )
          .post(s"$nuDesignerHttpAddress/api/processManagement/customAction/$exampleScenarioName/validation")
          .Then()
          .statusCode(200)
          .equalsJsonBody(
            s"""{
               |    "validationErrors": [
               |        {
               |            "typ": "MismatchParameter",
               |            "message": "Couldn't find a matching parameter in action definition for this param: property1",
               |            "description": "",
               |            "fieldName": "property1",
               |            "errorType": "SaveAllowed",
               |            "details": null
               |        },
               |        {
               |            "typ": "MismatchParameter",
               |            "message": "Couldn't find a matching parameter in action definition for this param: property2",
               |            "description": "",
               |            "fieldName": "property2",
               |            "errorType": "SaveAllowed",
               |            "details": null
               |        }
               |    ],
               |    "validationPerformed": true
               |}""".stripMargin
          )
      }
    }

    "not validate improper request and return error for" - {
      "non existing scenario" in {
        val wrongScenarioName = s"KochamCracovie"

        given()
          .applicationState {
            createDeployedScenario(exampleScenario)
          }
          .when()
          .basicAuthAllPermUser()
          .jsonBody(
            s"""{
             |  "actionName": "non-existing",
             |  "params": {
             |    "property1": "abc",
             |    "property2": "xyz"
             |  }
             |}
             |""".stripMargin
          )
          .post(s"$nuDesignerHttpAddress/api/processManagement/customAction/$wrongScenarioName/validation")
          .Then()
          .statusCode(404)
          .equalsPlainBody(s"Couldn't find $wrongScenarioName when trying to validate action".stripMargin)
      }

      "non existing action" in {
        given()
          .applicationState {
            createDeployedScenario(exampleScenario)
          }
          .when()
          .basicAuthAllPermUser()
          .jsonBody(
            s"""{
               |  "actionName": "non-existing",
               |  "params": {
               |    "property1": "abc",
               |    "property2": "xyz"
               |  }
               |}
               |""".stripMargin
          )
          .post(s"$nuDesignerHttpAddress/api/processManagement/customAction/$exampleScenarioName/validation")
          .Then()
          .statusCode(404)
          .equalsPlainBody(
            s"Couldn't find definition of action non-existing for scenario $exampleScenarioName when trying to validate".stripMargin
          )
      }
    }
  }

}
