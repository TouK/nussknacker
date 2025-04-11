package pl.touk.nussknacker.ui.api.testing

import io.circe.syntax.EncoderOps
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers.equalTo
import pl.touk.nussknacker.test.NuRestAssureExtensions.{AppConfiguration, EqualsJsonBody, JsonBody}
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.processes.WithScenarioActivitySpecAsserts.UsersBasicAuth
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.AdhocTestParametersRequest

trait WithAdHocTestsLogic {
  self: WithAdHocTestParameters with WithSimplifiedConfigScenarioHelper with NuItTest =>

  def shouldValidateParametersProperly(): Unit = {
    val request = AdhocTestParametersRequest(
      validParameters,
      exampleScenarioGraph
    ).asJson.toString()

    given()
      .applicationState {
        createSavedScenario(exampleScenario)
      }
      .when()
      .basicAuthAllPermUser()
      .jsonBody(request)
      .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${exampleScenario.name}/adhoc/validate")
      .Then()
      .statusCode(200)
      .equalsJsonBody(
        s"""{
           |    "validationErrors": [],
           |    "validationPerformed": true
           |}""".stripMargin
      )
  }

  def shouldReturnErrorsForInvalidParameters(): Unit = {
    val request = AdhocTestParametersRequest(
      invalidParameters,
      exampleScenarioGraph
    ).asJson.toString()

    given()
      .applicationState {
        createSavedScenario(exampleScenario)
      }
      .when()
      .basicAuthAllPermUser()
      .jsonBody(request)
      .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${exampleScenario.name}/adhoc/validate")
      .Then()
      .statusCode(200)
      .equalsJsonBody(
        s"""{
           |    "validationErrors": $expectedValidationErrorsOnInvalidParametersJson,
           |    "validationPerformed": true
           |}
           |""".stripMargin
      )
  }

  def shouldProperlyRunAdHocTest(): Unit = {
    given()
      .applicationState {
        createSavedScenario(exampleScenario)
      }
      .when()
      .basicAuthAllPermUser()
      .jsonBody(parametersProvidedForDryRun)
      .post(s"$nuDesignerHttpAddress/api/processManagement/testWithParameters/${exampleScenario.name}")
      .Then()
      .statusCode(200)
      .body(
        s"counts.$exampleScenarioSourceId.all",
        equalTo(1),
        "counts.end.all",
        equalTo(1)
      )
  }

  def shouldProperlyGetTestParameters(): Unit = {
    val request = exampleScenarioGraph.asJson.noSpaces

    given()
      .applicationState {
        createSavedScenario(exampleScenario)
      }
      .when()
      .basicAuthAllPermUser()
      .jsonBody(request)
      .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${exampleScenario.name}/parameters")
      .Then()
      .statusCode(200)
      .equalsJsonBody(
        expectedTestParametersJson
      )
  }

}
