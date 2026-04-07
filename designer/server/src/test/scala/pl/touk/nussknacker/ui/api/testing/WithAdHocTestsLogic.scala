package pl.touk.nussknacker.ui.api.testing

import io.circe.parser
import io.circe.syntax.EncoderOps
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers.equalTo
import org.scalatest.Suite
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.graph.node.SourceNodeData
import pl.touk.nussknacker.test.NuRestAssureExtensions.{AppConfiguration, EqualsJsonBody, JsonBody}
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.processes.WithScenarioActivitySpecAsserts.UsersBasicAuth
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.TestSourceParameters
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Capabilities.SourceCapabilitiesRequestDto
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.ScenarioTestData
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Validate.{
  ScenarioTestValidationRequest,
  SourceTestValidationRequestDto
}

trait WithAdHocTestsLogic {
  self: Suite with WithSimplifiedConfigScenarioHelper with NuItTest =>

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

  def shouldValidateSourceParametersProperly(): Unit = {
    val requestBody = SourceTestValidationRequestDto(
      processProperties = exampleScenario.toScenarioGraph.properties,
      nodeData = exampleSource,
      sourceParameters = validParameters,
    ).asJson.noSpaces

    given()
      .applicationState {
        createSavedScenario(exampleScenario)
      }
      .when()
      .basicAuthAllPermUser()
      .jsonBody(requestBody)
      .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${exampleScenario.name}/sourceValidate")
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

  def shouldProperlyGetTestCapabilities(): Unit = {
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

  def shouldProperlyGetTestSourceCapabilities(): Unit = {
    val requestBody =
      SourceCapabilitiesRequestDto(exampleScenario.toScenarioGraph.properties, exampleSource).asJson.noSpaces

    given()
      .applicationState {
        createSavedScenario(exampleScenario)
      }
      .when()
      .basicAuthAllPermUser()
      .jsonBody(requestBody)
      .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${exampleScenario.name}/sourceCapabilities")
      .Then()
      .statusCode(200)
      .equalsJsonBody(
        s"""{
           |    "testWithParameters": {
           |      "status": "AVAILABLE",
           |      "sourceParameters": $expectedTestSourceParametersJson
           |    }
           |}""".stripMargin
      )
  }

  def responseWithParameters(parametersJson: String): String =
    s"""{
       |    "testWithParameters": {
       |      "status": "AVAILABLE",
       |      "sourceParameters": $parametersJson
       |    },
       |    "testWithGeneratedData": {
       |      "status": "AVAILABLE"
       |    },
       |    "testWithLiveData": {
       |      "status": "AVAILABLE"
       |    }
       |}""".stripMargin

  protected def exampleScenarioSourceId: String

  protected def exampleScenario: CanonicalProcess

  protected def exampleSource: SourceNodeData =
    exampleScenario.nodes
      .collectFirst { case FlatNode(s: SourceNodeData) => s }
      .getOrElse(fail("No source node found in exampleScenario"))

  protected def validParameters: TestSourceParameters

  protected def expectedTestParametersJson: String

  protected def expectedTestSourceParametersJson: String =
    io.circe.parser
      .parse(expectedTestParametersJson)
      .getOrElse(fail("Invalid JSON in expectedTestParametersJson"))
      .asArray
      .flatMap(_.headOption)
      .getOrElse(fail("Expected parameters JSON should be an array with at least one element"))
      .noSpaces

  protected def exampleScenarioGraph: ScenarioGraph = exampleScenario.toScenarioGraph

}
