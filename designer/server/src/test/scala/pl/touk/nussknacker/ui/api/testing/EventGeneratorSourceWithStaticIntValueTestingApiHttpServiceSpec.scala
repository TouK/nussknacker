package pl.touk.nussknacker.ui.api.testing

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.TestSourceParameters
import pl.touk.nussknacker.ui.process.test.testdataformat.CommonDataFormatHandler.InputVariablesParameterName

class EventGeneratorSourceWithStaticIntValueTestingApiHttpServiceSpec
    extends EventGeneratorSourceTestingApiHttpServiceSpec {

  override protected def eventGeneratorValue: Expression = "5".spel

  override protected def filteringExpression: Expression = "#input != 666".spel

  override protected def exampleScenarioInputVariableType: TypingResult = Typed[Int]

  override protected def validParameters: TestSourceParameters =
    TestSourceParameters(
      exampleScenarioSourceId,
      Map(InputVariablesParameterName -> """{"input": 123}""".jsonExpression)
    )

  override protected def expectedTestDataJson: String =
    s"""[
       |  {"sourceId":"eventGeneratorSourceId","variables":{"input": 5}},
       |  {"sourceId":"eventGeneratorSourceId","variables":{"input": 5}},
       |  {"sourceId":"eventGeneratorSourceId","variables":{"input": 5}}
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
       |          "display": "Record{input: Integer}",
       |          "type": "TypedObjectTypingResult",
       |          "fields": {
       |            "input": {
       |              "display": "Integer",
       |              "type": "TypedClass",
       |              "refClazzName": "java.lang.Integer",
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
       |              "display": "Integer",
       |              "type": "TypedClass",
       |              "refClazzName": "java.lang.Integer",
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
       |          "expression": "{\\n  \\"input\\" : 0\\n}"
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

}
