package pl.touk.nussknacker.ui.api

import io.circe.syntax.EncoderOps
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.development.manager.MockableDeploymentManagerProvider.MockableDeploymentManager
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{
  WithBusinessCaseRestAssuredUsersExtensions,
  WithMockableDeploymentManager,
  WithSimplifiedDesignerConfig
}
import pl.touk.nussknacker.test.{
  NuRestAssureExtensions,
  NuRestAssureMatchers,
  RestAssuredVerboseLoggingIfValidationFails
}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter.toScenarioGraph

import java.util.UUID

class ManagementApiHttpServiceBusinessSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithSimplifiedConfigScenarioHelper
    with WithMockableDeploymentManager
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails {

  "The endpoint for testing with parameters should" - {
    "return empty results when empty results are returned by the deployment manager" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
          MockableDeploymentManager.configureTestResults(
            Map(
              exampleScenario.name.value -> TestResults(
                nodeResults = Map.empty,
                invocationResults = Map.empty,
                externalInvocationResults = Map.empty,
                exceptions = List.empty,
              )
            )
          )
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(s"""{
            | "sourceParameters": {
            |   "sourceId": "1",
            |   "parameterExpressions": {
            |     "param1": {
            |       "language": "spel",
            |       "expression": "1"
            |     },
            |     "param2": {
            |       "language": "spel",
            |       "expression": "test"
            |     }
            |   }
            | },
            | "scenarioGraph": ${toScenarioGraph(exampleScenario).asJson.spaces2}
            |}""".stripMargin)
        .post(s"$nuDesignerHttpAddress/api/processManagement/testWithParameters/${exampleScenario.name}")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          s"""
             |{
             |  "results": {
             |    "nodeResults": {},
             |    "invocationResults": {},
             |    "externalInvocationResults": {},
             |    "exceptions": []
             |  },
             |  "counts": {
             |    "sourceId": {
             |      "all": 0,
             |      "errors": 0,
             |      "fragmentCounts": {}
             |    },
             |    "sinkId": {
             |      "all": 0,
             |      "errors": 0,
             |      "fragmentCounts": {}
             |    }
             |  }
             |}
             |""".stripMargin
        )
    }
  }

  private lazy val exampleScenarioName = UUID.randomUUID().toString

  private lazy val exampleScenario = ScenarioBuilder
    .streaming(exampleScenarioName)
    .source("sourceId", "barSource")
    .emptySink("sinkId", "barSink")

  "The endpoint for customActions should " - {
    "return valid custom actions for Scenario " in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)

          MockableDeploymentManager.configure(
            Map(exampleScenario.name.value -> SimpleStateStatus.NotDeployed)
          )
        }
        .when()
        .basicAuthAllPermUser()
        .get(s"$nuDesignerHttpAddress/api/processManagement/customAction/$exampleScenarioName/1")
        .Then()
        .statusCode(200)
        .equalsJsonBody(s"""[
                           |    "hello",
                           |    "not-implemented",
                           |    "some-params-action"
                           |]""".stripMargin)
    }
  }

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
          .equalsPlainBody(s"Couldn't find scenario $wrongScenarioName".stripMargin)
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
            s"Couldn't find definition of action non-existing for scenario $exampleScenarioName".stripMargin
          )
      }
    }
  }

}
