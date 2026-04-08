package pl.touk.nussknacker.ui.api.testing

import io.circe.syntax.EncoderOps
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers.equalTo
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.test.NuRestAssureExtensions.{AppConfiguration, EqualsJsonBody, JsonBody}
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.processes.WithScenarioActivitySpecAsserts.UsersBasicAuth
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.TestSourceParameters
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.ScenarioTestData
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Validate.ScenarioTestValidationRequest

trait WithAdHocTestsLogic {
  self: WithSimplifiedConfigScenarioHelper with NuItTest =>

  def shouldValidateParametersProperly(): Unit = {
    val request = ScenarioTestValidationRequest(
      testData = ScenarioTestData.WithParameters(validParameters),
      scenarioGraph = exampleScenario.toScenarioGraph
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
           |    "validationErrors": [],
           |    "validationPerformed": true
           |}""".stripMargin
      )
  }

  def shouldProperlyRunAdHocTest(): Unit = {
    given()
      .applicationState {
        createSavedScenario(exampleScenario)
      }
      .when()
      .basicAuthAllPermUser()
      .jsonBody(
        ScenarioTestValidationRequest(
          testData = ScenarioTestData.WithParameters(validParameters),
          scenarioGraph = exampleScenario.toScenarioGraph
        ).asJson.toString()
      )
      .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${exampleScenario.name}/performTest")
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
      .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${exampleScenario.name}/capabilities")
      .Then()
      .statusCode(200)
      .equalsJsonBody(responseWithParameters(expectedTestParametersJson))
  }

  def responseWithParameters(parametersJson: String): String =
    s"""{
       |    "testWithParameters": {
       |      "status": "AVAILABLE",
       |      "sourceParameters": $parametersJson
       |    },
       |    "testWithLiveData": {
       |      "status": "AVAILABLE"
       |    }
       |}""".stripMargin

  protected def exampleScenarioSourceId: String

  protected def exampleScenario: CanonicalProcess

  protected def validParameters: TestSourceParameters

  protected def expectedTestParametersJson: String

  protected def exampleScenarioGraph: ScenarioGraph = exampleScenario.toScenarioGraph

}
