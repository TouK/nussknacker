package pl.touk.nussknacker.ui.api.testing

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.TestSourceParameters
import pl.touk.nussknacker.ui.process.test.testdataformat.CommonDataFormatHandler.InputVariablesParameterName

import java.time.LocalDateTime

class EventGeneratorSourceWithRecordValueTestingApiHttpServiceSpec
    extends EventGeneratorSourceTestingApiHttpServiceSpec {

  override protected def eventGeneratorValue: Expression =
    "{someNumber: 5, someString: 'alfa', date: #DATE_FORMAT.parseLocalDateTime('2025-01-31T10:11:12')}".spel

  override protected def filteringExpression: Expression = "#input.someNumber != 666".spel

  override protected def exampleScenarioInputVariableType: TypingResult = Typed.record(
    List(
      "someNumber" -> Typed[Int],
      "someString" -> Typed[String],
      "date"       -> Typed[LocalDateTime],
    )
  )

  override protected def validParameters: TestSourceParameters =
    TestSourceParameters(
      exampleScenarioSourceId,
      Map(
        InputVariablesParameterName -> """{"input": {"someNumber": 123, "someString": "asdf", "date": "2025-02-01T10:11:12"}}""".jsonExpression
      )
    )

  override protected def expectedTestDataJson: String =
    s"""[
       |  {"sourceId":"eventGeneratorSourceId","variables":{"input":{"someNumber":5,"someString":"alfa","date":"2025-01-31T10:11:12"}}},
       |  {"sourceId":"eventGeneratorSourceId","variables":{"input":{"someNumber":5,"someString":"alfa","date":"2025-01-31T10:11:12"}}},
       |  {"sourceId":"eventGeneratorSourceId","variables":{"input":{"someNumber":5,"someString":"alfa","date":"2025-01-31T10:11:12"}}}
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
       |          "display": "Record{input: Record{date: LocalDateTime, someNumber: Integer, someString: String}}",
       |          "type": "TypedObjectTypingResult",
       |          "fields": {
       |            "input": {
       |              "display": "Record{date: LocalDateTime, someNumber: Integer, someString: String}",
       |              "type": "TypedObjectTypingResult",
       |              "fields": {
       |                "someNumber": {
       |                  "display": "Integer",
       |                  "type": "TypedClass",
       |                  "refClazzName": "java.lang.Integer",
       |                  "params": []
       |                },
       |                "someString": {
       |                  "display": "String",
       |                  "type": "TypedClass",
       |                  "refClazzName": "java.lang.String",
       |                  "params": []
       |                },
       |                "date": {
       |                  "display": "LocalDateTime",
       |                  "type": "TypedClass",
       |                  "refClazzName": "java.time.LocalDateTime",
       |                  "params": []
       |                }
       |              },
       |              "refClazzName": "java.util.Map",
       |              "params": [
       |                {
       |                  "display": "String",
       |                  "type": "TypedClass",
       |                  "refClazzName": "java.lang.String",
       |                  "params": []
       |                },
       |                {
       |                  "display": "Unknown",
       |                  "type": "Unknown",
       |                  "refClazzName": "java.lang.Object",
       |                  "params": []
       |                }
       |              ]
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
       |              "display": "Record{date: LocalDateTime, someNumber: Integer, someString: String}",
       |              "type": "TypedObjectTypingResult",
       |              "fields": {
       |                "someNumber": {
       |                  "display": "Integer",
       |                  "type": "TypedClass",
       |                  "refClazzName": "java.lang.Integer",
       |                  "params": []
       |                },
       |                "someString": {
       |                  "display": "String",
       |                  "type": "TypedClass",
       |                  "refClazzName": "java.lang.String",
       |                  "params": []
       |                },
       |                "date": {
       |                  "display": "LocalDateTime",
       |                  "type": "TypedClass",
       |                  "refClazzName": "java.time.LocalDateTime",
       |                  "params": []
       |                }
       |              },
       |              "refClazzName": "java.util.Map",
       |              "params": [
       |                {
       |                  "display": "String",
       |                  "type": "TypedClass",
       |                  "refClazzName": "java.lang.String",
       |                  "params": []
       |                },
       |                {
       |                  "display": "Unknown",
       |                  "type": "Unknown",
       |                  "refClazzName": "java.lang.Object",
       |                  "params": []
       |                }
       |              ]
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
       |          "expression": "{\\n  \\"input\\" : {\\n    \\"someNumber\\" : 0,\\n    \\"someString\\" : \\"\\",\\n    \\"date\\" : \\"1900-01-01T00:00:00\\"\\n  }\\n}"
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
