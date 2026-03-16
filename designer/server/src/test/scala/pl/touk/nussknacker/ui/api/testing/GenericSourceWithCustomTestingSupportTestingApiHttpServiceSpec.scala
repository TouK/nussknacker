package pl.touk.nussknacker.ui.api.testing

import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.test.EitherValuesDetailedMessage
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.TestSourceParameters
import pl.touk.nussknacker.ui.process.test.testdataformat.CommonDataFormatHandler.InputVariablesParameterName
import sttp.client3.Response

class GenericSourceWithCustomTestingSupportTestingApiHttpServiceSpec
    extends ScenarioTestingApiHttpServiceGenericSpec
    with WithAdHocInvalidParametersTestsLogic
    with EitherValuesDetailedMessage
    with Matchers {

  override protected def exampleScenarioSourceId = "sourceId"

  override protected def exampleScenario: CanonicalProcess =
    ScenarioBuilder
      .streaming("scenario_1")
      .source(exampleScenarioSourceId, "genericSourceWithCustomTestingSupport", "elements" -> "{'test'}".spel)
      .emptySink("end", "monitor")

  override protected def exampleScenarioInputVariableType: TypingResult = Typed[String]

  override protected def expectedTestDataJson: String =
    s"""[
       |  {"sourceId":"sourceId","variables":{"input": "test-0"},"upstreamTimestamp":"1970-01-01T00:00:00.123Z"},
       |  {"sourceId":"sourceId","variables":{"input": "test-1"},"upstreamTimestamp":"1970-01-01T00:00:00.123Z"},
       |  {"sourceId":"sourceId","variables":{"input": "test-2"},"upstreamTimestamp":"1970-01-01T00:00:00.123Z"}
       |]""".stripMargin

  override protected def validParameters: TestSourceParameters =
    TestSourceParameters(
      exampleScenarioSourceId,
      Map(InputVariablesParameterName -> """{"input": "foobar"}""".jsonExpression)
    )

  override protected def invalidParameters: TestSourceParameters =
    TestSourceParameters(
      exampleScenarioSourceId,
      Map(InputVariablesParameterName -> """{"input": 123}""".jsonExpression)
    )

  override protected def expectedValidationErrorsOnInvalidParametersJson: String =
    s"""
       |[
       |  {
       |    "typ": "ExpressionParserCompilationError",
       |    "message": "Bad expression type, expected: List[String], found: Long(0)",
       |    "description": "There is problem with expression in field [elements] - it could not be parsed.",
       |    "fieldName": "elements",
       |    "errorType": "SaveAllowed",
       |    "details": null
       |  }
       |]""".stripMargin

  override protected def expectedTestParametersJson: String = {
    s"""[
       |  {
       |    "sourceId": "$exampleScenarioSourceId",
       |    "sourceName": "$exampleScenarioSourceId",
       |    "parameters": [
       |      {
       |        "name": "$InputVariablesParameterName",
       |        "typ": {
       |          "display": "Record{input: String}",
       |          "type": "TypedObjectTypingResult",
       |          "fields": {
       |            "input": {
       |              "display": "String",
       |              "type": "TypedClass",
       |              "refClazzName": "java.lang.String",
       |              "params": []
       |            }
       |          },
       |          "refClazzName": "java.util.Map",
       |          "params": [
       |            {
       |              "display": "String",
       |              "type": "TypedClass",
       |              "refClazzName": "java.lang.String",
       |              "params": []
       |            },
       |            {
       |              "display": "String",
       |              "type": "TypedClass",
       |              "refClazzName": "java.lang.String",
       |              "params": []
       |            }
       |          ]
       |        },
       |        "editors": [
       |          {
       |            "type": "JsonParameterEditor"
       |          }
       |        ],
       |        "defaultValue": {
       |          "language": "json",
       |          "expression": "{\\n  \\"input\\" : \\"\\"\\n}"
       |        },
       |        "additionalVariables": {},
       |        "variablesToHide": [],
       |        "branchParam": false,
       |        "hintText": null,
       |        "label": "$InputVariablesParameterName",
       |        "requiredParam": true,
       |        "category": "Standard",
       |        "changesCanReloadParameters": false,
       |        "nonImportantForExecution": false
       |      }
       |    ]
       |  }
       |]""".stripMargin
  }

  override protected def verifyTestFromFileWithCorrectTestDataResponse(response: Response[String]): Assertion = {
    response.bodyAsJson.hcursor
      .downField("counts")
      .downField(exampleScenarioSourceId)
      .downField("all")
      .as[Int]
      .rightValue shouldBe 3
  }

}
