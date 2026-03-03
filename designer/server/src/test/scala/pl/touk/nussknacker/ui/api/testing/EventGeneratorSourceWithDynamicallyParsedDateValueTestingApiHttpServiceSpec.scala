package pl.touk.nussknacker.ui.api.testing

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.TestSourceParameters
import pl.touk.nussknacker.ui.process.test.testdataformat.CommonDataFormatHandler.InputVariablesParameterName

import java.time.LocalDateTime

class EventGeneratorSourceWithDynamicallyParsedDateValueTestingApiHttpServiceSpec
    extends EventGeneratorSourceTestingApiHttpServiceSpec {

  override protected def eventGeneratorValue: Expression = "#DATE_FORMAT.parseLocalDateTime('2025-01-31T10:11:12')".spel

  override protected def filteringExpression: Expression = "#input.isAfter('1000-01-01T00:00:00')".spel

  override protected def exampleScenarioInputVariableType: TypingResult = Typed[LocalDateTime]

  override protected def validParameters: TestSourceParameters =
    TestSourceParameters(
      exampleScenarioSourceId,
      Map(InputVariablesParameterName -> """{"input": "2025-02-01T10:11:12"}""".jsonExpression)
    )

  override protected def expectedTestDataJson: String =
    s"""[
       |  {"sourceId":"eventGeneratorSourceId","variables":{"input":"2025-01-31T10:11:12"}},
       |  {"sourceId":"eventGeneratorSourceId","variables":{"input":"2025-01-31T10:11:12"}},
       |  {"sourceId":"eventGeneratorSourceId","variables":{"input":"2025-01-31T10:11:12"}}
       |]""".stripMargin

  override protected def expectedTestParametersJson: String =
    s"""[
       |  {
       |    "sourceId": "$exampleScenarioSourceId",
       |    "sourceName": "$exampleScenarioSourceId",
       |    "parameters": [
       |      {
       |        "name": "$InputVariablesParameterName",
       |        "typ": {
       |            "display": "Record{input: LocalDateTime}",
       |            "type": "TypedObjectTypingResult",
       |            "fields": {
       |                "input": {
       |                    "display": "LocalDateTime",
       |                    "type": "TypedClass",
       |                    "refClazzName": "java.time.LocalDateTime",
       |                    "params": []
       |                }
       |            },
       |            "refClazzName": "java.util.Map",
       |            "params": [
       |                {
       |                    "display": "String",
       |                    "type": "TypedClass",
       |                    "refClazzName": "java.lang.String",
       |                    "params": []
       |                },
       |                {
       |                    "display": "LocalDateTime",
       |                    "type": "TypedClass",
       |                    "refClazzName": "java.time.LocalDateTime",
       |                    "params": []
       |                }
       |            ]
       |        },
       |        "editors": [
       |            {
       |                "type": "JsonParameterEditor"
       |            }
       |        ],
       |        "defaultValue": {
       |            "language": "json",
       |            "expression": "{\\n  \\"input\\" : \\"1900-01-01T00:00:00\\"\\n}"
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
       |]
       |""".stripMargin

  override protected val inputValueNotMatchingExpectedType: String = "\"illegal-date\""
}
