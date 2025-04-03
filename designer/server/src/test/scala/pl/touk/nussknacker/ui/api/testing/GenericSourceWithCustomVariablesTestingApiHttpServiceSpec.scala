package pl.touk.nussknacker.ui.api.testing

import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.{AdhocTestParametersRequest, TestSourceParameters}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter.toScenarioGraph

class GenericSourceWithCustomVariablesTestingApiHttpServiceSpec extends TestingApiHttpServiceSpec {

  override protected def exampleScenarioSourceId = "sourceId"

  override protected def exampleScenario: CanonicalProcess =
    ScenarioBuilder
      .streaming("scenario_1")
      .source(exampleScenarioSourceId, "genericSourceWithCustomVariables", "elements" -> "{'test'}".spel)
      .emptySink("end", "monitor")

  override protected def parametersProvidedForDryRun: String =
    AdhocTestParametersRequest(
      sourceParameters = validParameters,
      scenarioGraph = toScenarioGraph(exampleScenario)
    ).asJson.toString()

  override protected def expectedSourceTestingParametersJson: String =
    """
      |  {
      |    "name": "elements",
      |    "typ": {
      |      "display": "List[String]",
      |      "type": "TypedClass",
      |      "refClazzName": "java.util.List",
      |      "params": [
      |        {
      |          "display": "String",
      |          "type": "TypedClass",
      |          "refClazzName": "java.lang.String",
      |          "params": [
      |          ]
      |        }
      |      ]
      |    },
      |    "editors": [
      |      {
      |        "type": "SpelParameterEditor"
      |      }
      |    ],
      |    "defaultValue": {
      |      "language": "spel",
      |      "expression": "{}"
      |    },
      |    "additionalVariables": {
      |    },
      |    "variablesToHide": [
      |    ],
      |    "branchParam": false,
      |    "requiredParam": true,
      |    "hintText": null,
      |    "label": "elements",
      |    "category": "Standard"
      |  }
      |""".stripMargin

  override protected def expectedTestDataJson: String =
    s"""{"sourceId":"sourceId","record":"test-0"}
       |{"sourceId":"sourceId","record":"test-1"}
       |{"sourceId":"sourceId","record":"test-2"}""".stripMargin

  override protected def validParameters: TestSourceParameters =
    TestSourceParameters(exampleScenarioSourceId, Map(ParameterName("elements") -> "{'123'}".spel))

  override protected def invalidParameters: TestSourceParameters =
    TestSourceParameters(exampleScenarioSourceId, Map(ParameterName("elements") -> "0L".spel))

  override protected def expectedValidationErrorsOnInvalidParametersJson: String =
    s"""
       |[
       |  {
       |    "typ": "ExpressionParserCompilationError",
       |    "message": "Failed to parse expression: Bad expression type, expected: List[String], found: Long(0)",
       |    "description": "There is problem with expression in field Some(elements) - it could not be parsed.",
       |    "fieldName": "elements",
       |    "errorType": "SaveAllowed",
       |    "details": null
       |  }
       |]""".stripMargin

}
