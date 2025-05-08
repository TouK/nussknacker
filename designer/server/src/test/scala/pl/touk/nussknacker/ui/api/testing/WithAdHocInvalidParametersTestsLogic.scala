package pl.touk.nussknacker.ui.api.testing

import io.circe.syntax.EncoderOps
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import pl.touk.nussknacker.test.NuRestAssureExtensions.{AppConfiguration, EqualsJsonBody, JsonBody}
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.processes.WithScenarioActivitySpecAsserts.UsersBasicAuth
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.TestSourceParameters
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.ScenarioTestData
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Validate.ScenarioTestValidationRequest

trait WithAdHocInvalidParametersTestsLogic {
  self: WithAdHocTestsLogic with WithSimplifiedConfigScenarioHelper with NuItTest =>

  def shouldReturnErrorsForInvalidParameters(): Unit = {
    val request = ScenarioTestValidationRequest(
      exampleScenarioGraph,
      ScenarioTestData.WithParameters(invalidParameters),
    ).asJson.toString()

    given()
      .applicationState {
        createSavedScenario(exampleScenario)
      }
      .when()
      .basicAuthAllPermUser()
      .jsonBody(request)
      .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${exampleScenario.name}/validate")
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

  protected def invalidParameters: TestSourceParameters

  protected def expectedValidationErrorsOnInvalidParametersJson: String

}
