package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.given
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers
import org.hamcrest.Matchers.{empty, equalTo, is, not}
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.test.{NuRestAssureMatchers, PatientScalaFutures, RestAssuredVerboseLoggingIfValidationFails}
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{
  WithBusinessCaseRestAssuredUsersExtensions,
  WithMockableDeploymentManager,
  WithSimplifiedDesignerConfig
}

class NodesApiHttpServiceBusinessSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithSimplifiedConfigScenarioHelper
    with WithMockableDeploymentManager
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails
    with PatientScalaFutures
    with TableDrivenPropertyChecks {

  "The endpoint for nodes additional info should" - {
    "return additional info for node with expression in existing scenario" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(
          s"""{
             |  "id": "enricher",
             |  "service": {
             |    "id": "paramService",
             |    "parameters": [
             |      {
             |        "name": "id",
             |        "expression": {
             |          "language": "spel",
             |          "expression": "'a'"
             |        }
             |      }
             |    ]
             |  },
             |  "output": "out",
             |  "additionalFields": null,
             |  "type": "Enricher"
             |}""".stripMargin
        )
        .post(s"$nuDesignerHttpAddress/api/nodes/${exampleScenario.name}/additionalInfo")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          s"""{
             |  "content": "\\nSamples:\\n\\n| id  | value |\\n| --- | ----- |\\n| a   | generated |\\n| b   | not existent |\\n\\nResults for a can be found [here](http://touk.pl?id=a)\\n",
             |  "type": "MarkdownAdditionalInfo"
             |}""".stripMargin
        )
    }
    "return no additional info for node without expression in existing scenario" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(
          s"""{
             |  "id": "enricher",
             |  "service": {
             |    "id": "paramService",
             |    "parameters": []
             |  },
             |  "output": "out",
             |  "additionalFields": null,
             |  "type": "Enricher"
             |}""".stripMargin
        )
        .post(s"$nuDesignerHttpAddress/api/nodes/${exampleScenario.name}/additionalInfo")
        .Then()
        .statusCode(200)
        .body(
          equalTo("")
        )
    }
    "return 404 for not existent scenario" in {
      val nonExistingScenarioName = "nonExistingScenario"

      given()
        .when()
        .basicAuthAllPermUser()
        .jsonBody(exampleNodesAdditionalInfoRequestBody)
        .post(s"$nuDesignerHttpAddress/api/nodes/$nonExistingScenarioName/additionalInfo")
        .Then()
        .statusCode(404)
        .body(
          equalTo(s"No scenario $nonExistingScenarioName found")
        )
    }
  }

  "The endpoint for nodes validation should" - {
    "validate correct node without errors" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(
          s"""{
             |  "nodeData": {
             |    "id": "id",
             |    "expression": {
             |      "language": "spel",
             |      "expression": "#longValue > 1"
             |    },
             |    "isDisabled": null,
             |    "additionalFields": null,
             |    "type": "Filter"
             |  },
             |  "processProperties": {
             |    "isFragment": false,
             |    "additionalFields": {
             |      "description": null,
             |      "properties": {
             |        "parallelism": "",
             |        "spillStateToDisk": "true",
             |        "useAsyncInterpretation": "",
             |        "checkpointIntervalInSeconds": ""
             |      },
             |      "metaDataType": "StreamMetaData"
             |    }
             |  },
             |  "variableTypes": {
             |    "existButString": {
             |      "display": "String",
             |      "type": "TypedClass",
             |      "refClazzName": "java.lang.String",
             |      "params": []
             |    },
             |    "longValue": {
             |      "display": "Long",
             |      "type": "TypedClass",
             |      "refClazzName": "java.lang.Long",
             |      "params": []
             |    }
             |  },
             |  "branchVariableTypes": null,
             |  "outgoingEdges": null
             |}""".stripMargin
        )
        .post(s"$nuDesignerHttpAddress/api/nodes/${exampleScenario.name}/validation")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          s"""{
             |  "parameters": null,
             |  "expressionType": {
             |    "display": "Boolean",
             |    "type": "TypedClass",
             |    "refClazzName": "java.lang.Boolean",
             |    "params": []
             |  },
             |  "validationErrors": [],
             |  "validationPerformed": true
             |}""".stripMargin
        )
    }
    "validate filter node when wrong parameter type is given" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(
          s"""{
             |  "nodeData": {
             |    "id": "id",
             |    "expression": {
             |      "language": "spel",
             |      "expression": "#existButString"
             |    },
             |    "isDisabled": null,
             |    "additionalFields": null,
             |    "type": "Filter"
             |  },
             |  "processProperties": {
             |    "isFragment": false,
             |    "additionalFields": {
             |      "description": null,
             |      "properties": {
             |        "parallelism": "",
             |        "spillStateToDisk": "true",
             |        "useAsyncInterpretation": "",
             |        "checkpointIntervalInSeconds": ""
             |      },
             |      "metaDataType": "StreamMetaData"
             |    }
             |  },
             |  "variableTypes": {
             |    "existButString": {
             |      "display": "String",
             |      "type": "TypedClass",
             |      "refClazzName": "java.lang.String",
             |      "params": []
             |    },
             |    "longValue": {
             |      "display": "Long",
             |      "type": "TypedClass",
             |      "refClazzName": "java.lang.Long",
             |      "params": []
             |    }
             |  },
             |  "branchVariableTypes": null,
             |  "outgoingEdges": null
             |}""".stripMargin
        )
        .post(s"$nuDesignerHttpAddress/api/nodes/${exampleScenario.name}/validation")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          s"""{
             |  "parameters": null,
             |  "expressionType": {
             |    "display": "Unknown",
             |    "type": "Unknown",
             |    "refClazzName": "java.lang.Object",
             |    "params": []
             |  },
             |  "validationErrors": [
             |    {
             |      "typ": "ExpressionParserCompilationError",
             |      "message": "Bad expression type, expected: Boolean, found: String",
             |      "description": "There is problem with expression in field [$$expression] - it could not be parsed.",
             |      "fieldName": "$$expression",
             |      "errorType": "SaveAllowed",
             |      "details": null
             |    }
             |  ],
             |  "validationPerformed": true
             |}""".stripMargin
        )
    }
    "validate incorrect sink expression" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(
          s"""{
             |  "nodeData": {
             |    "id": "mysink",
             |    "ref": {
             |      "typ": "kafka-string",
             |      "parameters": [
             |        {
             |          "name": "Value",
             |          "expression": {
             |            "language": "spel",
             |            "expression": "notvalidspelexpression"
             |          }
             |        },
             |        {
             |          "name": "Topic",
             |          "expression": {
             |            "language": "spel",
             |            "expression": "'test-topic'"
             |          }
             |        }
             |      ]
             |    },
             |    "endResult": null,
             |    "isDisabled": null,
             |    "additionalFields": null,
             |    "type": "Sink"
             |  },
             |  "processProperties": {
             |    "isFragment": false,
             |    "additionalFields": {
             |      "description": null,
             |      "properties": {
             |        "parallelism": "",
             |        "spillStateToDisk": "true",
             |        "useAsyncInterpretation": "",
             |        "checkpointIntervalInSeconds": ""
             |      },
             |      "metaDataType": "StreamMetaData"
             |    }
             |  },
             |  "variableTypes": {
             |    "existButString": {
             |      "display": "String",
             |      "type": "TypedClass",
             |      "refClazzName": "java.lang.String",
             |      "params": []
             |    },
             |    "longValue": {
             |      "display": "Long",
             |      "type": "TypedClass",
             |      "refClazzName": "java.lang.Long",
             |      "params": []
             |    }
             |  },
             |  "branchVariableTypes": null,
             |  "outgoingEdges": null
             |}""".stripMargin
        )
        .post(s"$nuDesignerHttpAddress/api/nodes/${exampleScenario.name}/validation")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          s"""{
             |  "parameters": [
             |    {
             |      "name": "Topic",
             |      "typ": {
             |        "display": "String",
             |        "type": "TypedClass",
             |        "refClazzName": "java.lang.String",
             |        "params": []
             |      },
             |      "editors": [
             |        {
             |          "type": "SpelTemplateParameterEditor"
             |        },
             |        {
             |          "type": "SpelParameterEditor"
             |        }
             |      ],
             |      "defaultValue": {
             |        "language": "spelTemplate",
             |        "expression": ""
             |      },
             |      "additionalVariables": {},
             |      "variablesToHide": [],
             |      "branchParam": false,
             |      "requiredParam": true,
             |      "hintText": null,
             |      "label": "Topic",
             |      "category": "Standard",
             |      "changesCanReloadParameters": false,
             |      "nonImportantForExecution": false
             |    },
             |    {
             |      "name": "Value",
             |      "typ": {
             |        "display": "Unknown",
             |        "type": "Unknown",
             |        "refClazzName": "java.lang.Object",
             |        "params": []
             |      },
             |      "editors": [{
             |        "type": "SpelParameterEditor"
             |      }],
             |      "defaultValue": {
             |        "language": "spel",
             |        "expression": ""
             |      },
             |      "additionalVariables": {},
             |      "variablesToHide": [],
             |      "branchParam": false,
             |      "requiredParam": true,
             |      "hintText": null,
             |      "label": "Value",
             |      "category": "Standard",
             |      "changesCanReloadParameters": false,
             |      "nonImportantForExecution": false
             |    }
             |  ],
             |  "expressionType": null,
             |  "validationErrors": [
             |    {
             |      "typ": "ExpressionParserCompilationError",
             |      "message": "Non reference 'notvalidspelexpression' occurred. Maybe you missed '#' in front of it?",
             |      "description": "There is problem with expression in field [Value] - it could not be parsed.",
             |      "fieldName": "Value",
             |      "errorType": "SaveAllowed",
             |      "details": {
             |        "start": {
             |          "column": 0,
             |          "row": 0
             |        },
             |        "end": {
             |          "column": 22,
             |          "row": 0
             |        },
             |        "type": "CoordinatesBasedTextRange"
             |      }
             |    }
             |  ],
             |  "validationPerformed": true
             |}""".stripMargin
        )
    }
    "validate node using dictionaries" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(
          s"""{
             |  "nodeData": {
             |    "id": "id",
             |    "expression": {
             |      "language": "spel",
             |      "expression": "#DICT.Bar != #DICT.Foo"
             |    },
             |    "isDisabled": null,
             |    "additionalFields": null,
             |    "type": "Filter"
             |  },
             |  "processProperties": {
             |    "isFragment": false,
             |    "additionalFields": {
             |      "description": null,
             |      "properties": {
             |        "parallelism": "",
             |        "spillStateToDisk": "true",
             |        "useAsyncInterpretation": "",
             |        "checkpointIntervalInSeconds": ""
             |      },
             |      "metaDataType": "StreamMetaData"
             |    }
             |  },
             |  "variableTypes": {},
             |  "branchVariableTypes": null,
             |  "outgoingEdges": null
             |}""".stripMargin
        )
        .post(s"$nuDesignerHttpAddress/api/nodes/${exampleScenario.name}/validation")
        .Then()
        .statusCode(200)
        .equalsJsonBody(s"""{
             |  "parameters": null,
             |  "expressionType": {
             |    "display": "Boolean",
             |    "type": "TypedClass",
             |    "refClazzName": "java.lang.Boolean",
             |    "params": []
             |  },
             |  "validationErrors": [],
             |  "validationPerformed": true
             |}""".stripMargin)
    }
    "validate node id" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(
          s"""{
             |  "nodeData": {
             |    "id": " ",
             |    "expression": {
             |      "language": "spel",
             |      "expression": "true"
             |    },
             |    "isDisabled": null,
             |    "additionalFields": null,
             |    "type": "Filter"
             |  },
             |  "processProperties": {
             |    "isFragment": false,
             |    "additionalFields": {
             |      "description": null,
             |      "properties": {
             |        "parallelism": "",
             |        "spillStateToDisk": "true",
             |        "useAsyncInterpretation": "",
             |        "checkpointIntervalInSeconds": ""
             |      },
             |      "metaDataType": "StreamMetaData"
             |    }
             |  },
             |  "variableTypes": {},
             |  "branchVariableTypes": null,
             |  "outgoingEdges": null
             |}""".stripMargin
        )
        .post(s"$nuDesignerHttpAddress/api/nodes/${exampleScenario.name}/validation")
        .Then()
        .statusCode(200)
        .equalsJsonBody(s"""{
             |  "parameters": null,
             |  "expressionType": {
             |    "value": true,
             |    "display": "Boolean(true)",
             |    "type": "TypedObjectWithValue",
             |    "refClazzName": "java.lang.Boolean",
             |    "params": []
             |  },
             |  "validationErrors": [
             |    {
             |      "typ": "NodeIdValidationError",
             |      "message": "Node name cannot be blank",
             |      "description": "Blank node name",
             |      "fieldName": "$$id",
             |      "errorType": "SaveAllowed",
             |      "details": null
             |    }
             |  ],
             |  "validationPerformed": true
             |}""".stripMargin)
    }

    "return 200 for fragment input node referencing fragment that doesn't exist" in {
      val nonExistingFragmentName = "non-existing-fragment"

      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .basicAuthAllPermUser()
        .jsonBody(exampleNodeValidationRequestForFragment(nonExistingFragmentName))
        .when()
        .post(s"$nuDesignerHttpAddress/api/nodes/${exampleScenario.name}/validation")
        .Then()
        .statusCode(200)
        .equalsJsonBody(s"""{
             |  "parameters": null,
             |  "expressionType": null,
             |  "validationErrors": [
             |    {
             |      "typ": "UnknownFragment",
             |      "message": "Unknown fragment",
             |      "description": "Node fragment uses fragment non-existing-fragment which is missing",
             |      "fieldName": null,
             |      "errorType": "SaveAllowed",
             |      "details": null
             |    }
             |  ],
             |  "validationPerformed": true
             |}""".stripMargin)
    }
  }

  "The endpoint for test case additional variables should" - {
    "return assertions variables and their types" in {
      given()
        .basicAuthAllPermUser()
        .jsonBody(
          """
            |{
            |  "variableTypes": {
            |    "input": {
            |      "display": "Record{dateTime: Instant, sampleField: String, type: String, value: Integer}",
            |      "type": "TypedObjectTypingResult",
            |      "fields": {
            |        "sampleField": {
            |          "display": "String",
            |          "type": "TypedClass",
            |          "refClazzName": "java.lang.String",
            |          "params": []
            |        },
            |        "dateTime": {
            |          "display": "Instant",
            |          "type": "TypedClass",
            |          "refClazzName": "java.time.Instant",
            |          "params": []
            |        },
            |        "type": {
            |          "display": "String",
            |          "type": "TypedClass",
            |          "refClazzName": "java.lang.String",
            |          "params": []
            |        },
            |        "value": {
            |          "display": "Integer",
            |          "type": "TypedClass",
            |          "refClazzName": "java.lang.Integer",
            |          "params": []
            |        }
            |      },
            |      "refClazzName": "java.util.Map",
            |      "params": [
            |        {
            |          "display": "String",
            |          "type": "TypedClass",
            |          "refClazzName": "java.lang.String",
            |          "params": []
            |        },
            |        {
            |          "display": "Unknown",
            |          "type": "Unknown",
            |          "refClazzName": "java.lang.Object",
            |          "params": []
            |        }
            |      ]
            |    },
            |    "sampleVariable": {
            |      "value": "sample value",
            |      "display": "String(value)",
            |      "type": "TypedObjectWithValue",
            |      "refClazzName": "java.lang.String",
            |      "params": []
            |    }
            |  }
            |}
            |""".stripMargin
        )
        .when()
        .post(s"$nuDesignerHttpAddress/api/nodes/${exampleScenario.name}/testCase/additionalVariables")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          """{
            |  "assertionsAdditionalVariables": {
            |    "TESTS": {
            |      "display": "tests",
            |      "type": "TypedClass",
            |      "refClazzName": "pl.touk.nussknacker.ui.process.test.testcase.tests$",
            |      "params": [
            |      ]
            |    },
            |    "contexts": {
            |      "display": "List[Record{input: Unknown, sampleVariable: Unknown}]",
            |      "type": "TypedClass",
            |      "refClazzName": "java.util.List",
            |      "params": [
            |        {
            |          "display": "Record{input: Unknown, sampleVariable: Unknown}",
            |          "type": "TypedObjectTypingResult",
            |          "fields": {
            |            "input": {
            |              "display": "Unknown",
            |              "type": "Unknown",
            |              "refClazzName": "java.lang.Object",
            |              "params": [
            |              ]
            |            },
            |            "sampleVariable": {
            |              "display": "Unknown",
            |              "type": "Unknown",
            |              "refClazzName": "java.lang.Object",
            |              "params": [
            |              ]
            |            }
            |          },
            |          "refClazzName": "java.util.Map",
            |          "params": [
            |            {
            |              "display": "String",
            |              "type": "TypedClass",
            |              "refClazzName": "java.lang.String",
            |              "params": [
            |              ]
            |            },
            |            {
            |              "display": "Unknown",
            |              "type": "Unknown",
            |              "refClazzName": "java.lang.Object",
            |              "params": [
            |              ]
            |            }
            |          ]
            |        }
            |      ]
            |    }
            |  }
            |}""".stripMargin
        )
    }

  }

  "The endpoint for properties additional info should" - {
    "return additional info for scenario properties" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(
          s"""{
             |  "isFragment": false,
             |  "additionalFields": {
             |    "description": null,
             |    "properties": {
             |      "parallelism": "",
             |      "checkpointIntervalInSeconds": "",
             |      "numberOfThreads": "2",
             |      "spillStateToDisk": "true",
             |      "environment": "test",
             |      "useAsyncInterpretation": ""
             |    },
             |    "metaDataType": "StreamMetaData"
             |  }
             |}""".stripMargin
        )
        .post(s"$nuDesignerHttpAddress/api/properties/${exampleScenario.name}/additionalInfo")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          s"""{
             |  "content": "2 threads will be used on environment 'test'",
             |  "type": "MarkdownAdditionalInfo"
             |}""".stripMargin
        )
    }
  }

  "The endpoint for properties validation should" - {
    "validate properties" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(
          s"""{
             |  "additionalFields": {
             |    "description": null,
             |    "properties": {
             |      "parallelism": "",
             |      "checkpointIntervalInSeconds": "",
             |      "numberOfThreads": "a",
             |      "spillStateToDisk": "true",
             |      "environment": "test",
             |      "useAsyncInterpretation": ""
             |    },
             |    "metaDataType": "StreamMetaData"
             |  },
             |  "name": "test"
             |}""".stripMargin
        )
        .post(s"$nuDesignerHttpAddress/api/properties/${exampleScenario.name}/validation")
        .Then()
        .statusCode(200)
        .equalsJsonBody(s"""{
             |  "parameters": null,
             |  "expressionType": null,
             |  "validationErrors": [
             |    {
             |      "typ": "InvalidPropertyFixedValue",
             |      "message": "Property numberOfThreads (Number of threads) has invalid value",
             |      "description": "Expected one of 1, 2, got: a.",
             |      "fieldName": "numberOfThreads",
             |      "errorType": "SaveAllowed",
             |      "details": {
             |      "paramName": "numberOfThreads",
             |      "parameterEditors": [
             |        {
             |          "possibleValues": [
             |            {
             |              "expression": "1",
             |              "label": "1"
             |            },
             |            {
             |              "expression": "2",
             |              "label": "2"
             |            }
             |          ],
             |          "type": "FixedValuesParameterEditor"
             |        }
             |      ],
             |      "nodeId": "properties",
             |      "type": "IncompatibleParameterDefinitionErrorDetails"
             |      }
             |    }
             |  ],
             |  "validationPerformed": true
             |}""".stripMargin)
    }
    "validate scenario id" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(
          s"""{
             |  "additionalFields": {
             |    "description": null,
             |    "properties": {
             |      "parallelism": "",
             |      "checkpointIntervalInSeconds": "",
             |      "numberOfThreads": "1",
             |      "spillStateToDisk": "true",
             |      "environment": "test",
             |      "useAsyncInterpretation": "true"
             |    },
             |    "metaDataType": "StreamMetaData"
             |  },
             |  "name": " "
             |}""".stripMargin
        )
        .post(s"$nuDesignerHttpAddress/api/properties/${exampleScenario.name}/validation")
        .Then()
        .statusCode(200)
        .equalsJsonBody(s"""{
             |  "parameters": null,
             |  "expressionType": null,
             |  "validationErrors": [
             |    {
             |      "typ": "ScenarioNameError",
             |      "message": "Scenario name cannot be blank",
             |      "description": "Blank scenario name",
             |      "fieldName": "$$id",
             |      "errorType": "SaveAllowed",
             |      "details": null
             |    },
             |    {
             |      "typ": "ScenarioNameValidationError",
             |      "message": "Invalid scenario name  . Only digits, letters, underscore (_), hyphen (-) and space in the middle are allowed",
             |      "description": "Provided scenario name is invalid for this category. Please enter valid name using only specified characters.",
             |      "fieldName": "$$id",
             |      "errorType": "SaveAllowed",
             |      "details": null
             |    }
             |  ],
             |  "validationPerformed": true
             |}""".stripMargin)
    }
  }

  "The endpoint for parameters validation" - {
    "validate correct parameter" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .jsonBody(
          s"""{
             |  "parameters": [
             |    {
             |      "name": "condition",
             |      "typ": {
             |        "display": "Boolean",
             |        "type": "TypedClass",
             |        "refClazzName": "java.lang.Boolean",
             |        "params": []
             |      },
             |      "expression": {
             |        "language": "spel",
             |        "expression": "#input.amount > 2"
             |      }
             |    }
             |  ],
             |  "variableTypes": {
             |    "input": {
             |      "display": "Record{amount: Long(5)}",
             |      "type": "TypedObjectTypingResult",
             |      "fields": {
             |        "amount": {
             |          "value": 5,
             |          "display": "Long(5)",
             |          "type": "TypedObjectWithValue",
             |          "refClazzName": "java.lang.Long",
             |          "params": []
             |        }
             |      },
             |      "refClazzName": "java.util.Map",
             |      "params": [
             |        {
             |          "display": "String",
             |          "type": "TypedClass",
             |          "refClazzName": "java.lang.String",
             |          "params": []
             |        },
             |        {
             |          "value": 5,
             |          "display": "Long(5)",
             |          "type": "TypedObjectWithValue",
             |          "refClazzName": "java.lang.Long",
             |          "params": []
             |        }
             |      ]
             |    }
             |  }
             |}""".stripMargin
        )
        .post(s"$nuDesignerHttpAddress/api/parameters/streaming/validate")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          s"""{
             |  "validationErrors": [],
             |  "validationPerformed": true
             |}""".stripMargin
        )
    }
    "validate incorrect parameter" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .jsonBody(
          s"""{
             |  "parameters": [
             |    {
             |      "name": "condition",
             |      "typ": {
             |        "display": "Boolean",
             |        "type": "TypedClass",
             |        "refClazzName": "java.lang.Boolean",
             |        "params": []
             |      },
             |      "expression": {
             |        "language": "spel",
             |        "expression": "#input.amount"
             |      }
             |    }
             |  ],
             |  "variableTypes": {
             |    "input": {
             |      "display": "Record{amount: Long(5)}",
             |      "type": "TypedObjectTypingResult",
             |      "fields": {
             |        "amount": {
             |          "value": 5,
             |          "display": "Long(5)",
             |          "type": "TypedObjectWithValue",
             |          "refClazzName": "java.lang.Long",
             |          "params": []
             |        }
             |      },
             |      "refClazzName": "java.util.Map",
             |      "params": [
             |        {
             |          "display": "String",
             |          "type": "TypedClass",
             |          "refClazzName": "java.lang.String",
             |          "params": []
             |        },
             |        {
             |          "value": 5,
             |          "display": "Long(5)",
             |          "type": "TypedObjectWithValue",
             |          "refClazzName": "java.lang.Long",
             |          "params": []
             |        }
             |      ]
             |    }
             |  }
             |}""".stripMargin
        )
        .post(s"$nuDesignerHttpAddress/api/parameters/streaming/validate")
        .Then()
        .statusCode(200)
        .equalsJsonBody(s"""{
             |  "validationErrors": [ {
             |    "typ": "ExpressionParserCompilationError",
             |    "message": "Bad expression type, expected: Boolean, found: Long(5)",
             |    "description": "There is problem with expression in field [condition] - it could not be parsed.",
             |    "fieldName": "condition",
             |    "errorType": "SaveAllowed",
             |    "details": null
             |  } ],
             |  "validationPerformed": true
             |}""".stripMargin)
    }

    "return 404 for not existent processing type" in {
      val notExistentProcessingType = "not-existent"
      given()
        .basicAuthAllPermUser()
        .jsonBody(exampleParametersValidationRequestBody)
        .when()
        .post(s"$nuDesignerHttpAddress/api/parameters/$notExistentProcessingType/validate")
        .Then()
        .statusCode(404)
        .body(
          equalTo(s"ProcessingType type: $notExistentProcessingType not found")
        )
    }
  }

  "The endpoint for parameters suggestions should" - {
    "suggest the name of parameter" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .jsonBody(
          s"""{
             |  "expression": {
             |    "language": "spel",
             |    "expression": "#inpu"
             |  },
             |  "caretPosition2d": {
             |    "row": 0,
             |    "column": 5
             |  },
             |  "variableTypes": {
             |    "input": {
             |      "display": "Record{amount: Long(5)}",
             |      "type": "TypedObjectTypingResult",
             |      "fields": {
             |        "amount": {
             |          "value": 5,
             |          "display": "Long(5)",
             |          "type": "TypedObjectWithValue",
             |          "refClazzName": "java.lang.Long",
             |          "params": []
             |        }
             |      },
             |      "refClazzName": "java.util.Map",
             |      "params": [
             |        {
             |          "display": "String",
             |          "type": "TypedClass",
             |          "refClazzName": "java.lang.String",
             |          "params": []
             |        },
             |        {
             |          "value": 5,
             |          "display": "Long(5)",
             |          "type": "TypedObjectWithValue",
             |          "refClazzName": "java.lang.Long",
             |          "params": []
             |        }
             |      ]
             |    }
             |  }
             |}""".stripMargin
        )
        .post(s"$nuDesignerHttpAddress/api/parameters/streaming/suggestions")
        .Then()
        .statusCode(200)
        .body("methodName[0]", equalTo("#input"))
    }
    "return proper suggestions for all global helpers" in {
      val helpers = List(
        "BASE64",
        "BusinessConfig",
        "COLLECTION",
        "CONV",
        "DATE",
        "DATE_FORMAT",
        "DICT",
        "GEO",
        "HelperFunction",
        "NUMERIC",
        "RANDOM",
        "RGB",
        "TypedConfig",
        "UTIL",
        "meta"
      )

      def requestSuggestions(expression: String) = {
        given()
          .when()
          .basicAuthAllPermUser()
          .jsonBody(
            s"""{
               |  "expression": {
               |    "language": "spel",
               |    "expression": "$expression"
               |  },
               |  "caretPosition2d": {
               |    "row": 0,
               |    "column": ${expression.length}
               |  },
               |  "variableTypes": {}
               |}""".stripMargin
          )
          .post(s"$nuDesignerHttpAddress/api/parameters/streaming/suggestions")
          .Then()
      }

      requestSuggestions("#")
        .statusCode(200)
        .body(
          "methodName",
          Matchers.contains(helpers.map(helper => s"#$helper"): _*)
        )

      forAll(Table[String]("helper", helpers: _*)) { helper =>
        requestSuggestions(s"#$helper.")
          .statusCode(200)
          .body("methodName", is(not(empty())))
      }
    }
    "not suggest anything if no such parameters exist" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .jsonBody(
          s"""{
             |  "expression": {
             |    "language": "spel",
             |    "expression": "#inpu"
             |  },
             |  "caretPosition2d": {
             |    "row": 0,
             |    "column": 5
             |  },
             |  "variableTypes": {}
             |}""".stripMargin
        )
        .post(s"$nuDesignerHttpAddress/api/parameters/streaming/suggestions")
        .Then()
        .statusCode(200)
        .body(equalTo("[]"))
    }
  }

  "The endpoint for fetching latest records should" - {
    "return records from source node" in {
      given()
        .applicationState {
          createSavedScenario(sourceTestingScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(
          s"""{
             |  "processProperties": {
             |    "additionalFields": {
             |      "properties": {
             |        "parallelism": "1",
             |        "spillStateToDisk": "true",
             |        "useAsyncInterpretation": "",
             |        "checkpointIntervalInSeconds": ""
             |      },
             |      "metaDataType": "StreamMetaData"
             |    }
             |  },
             |  "nodeData": {
             |    "id": "$sourceTestingScenarioSourceId",
             |    "type": "Source",
             |    "ref": {
             |      "typ": "event-generator",
             |      "parameters": [
             |        {
             |          "name": "value",
             |          "expression": {
             |            "language": "jsonTemplate",
             |            "expression": "{\\"value\\": \\"test-#{ #DATE.now.toEpochMilli }\\", \\"timestamp\\": 123}"
             |          }
             |        }
             |      ]
             |    }
             |  }
             |}""".stripMargin
        )
        .post(s"$nuDesignerHttpAddress/api/nodes/${sourceTestingScenario.name}/records?limit=10")
        .Then()
        .statusCode(200)
        .body(
          matchJsonWithRegexValues(
            s"""[
               |  {"sourceId":"sourceId","variables":{"input":{"value":"test-\\\\d+","timestamp":123}}},
               |  {"sourceId":"sourceId","variables":{"input":{"value":"test-\\\\d+","timestamp":123}}},
               |  {"sourceId":"sourceId","variables":{"input":{"value":"test-\\\\d+","timestamp":123}}},
               |  {"sourceId":"sourceId","variables":{"input":{"value":"test-\\\\d+","timestamp":123}}},
               |  {"sourceId":"sourceId","variables":{"input":{"value":"test-\\\\d+","timestamp":123}}},
               |  {"sourceId":"sourceId","variables":{"input":{"value":"test-\\\\d+","timestamp":123}}},
               |  {"sourceId":"sourceId","variables":{"input":{"value":"test-\\\\d+","timestamp":123}}},
               |  {"sourceId":"sourceId","variables":{"input":{"value":"test-\\\\d+","timestamp":123}}},
               |  {"sourceId":"sourceId","variables":{"input":{"value":"test-\\\\d+","timestamp":123}}},
               |  {"sourceId":"sourceId","variables":{"input":{"value":"test-\\\\d+","timestamp":123}}}
               |]""".stripMargin
          )
        )
    }

    "return error when node is not a source" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(
          s"""{
             |  "processProperties": {
             |    "additionalFields": {
             |      "properties": {
             |        "parallelism": "1",
             |        "spillStateToDisk": "true",
             |        "useAsyncInterpretation": "",
             |        "checkpointIntervalInSeconds": ""
             |      },
             |      "metaDataType": "StreamMetaData"
             |    }
             |  },
             |  "nodeData": {
             |    "id": "sinkId",
             |    "type": "Sink",
             |    "ref": {
             |      "typ": "barSink",
             |      "parameters": []
             |    }
             |  }
             |}""".stripMargin
        )
        .post(s"$nuDesignerHttpAddress/api/nodes/${exampleScenario.name}/records?limit=10")
        .Then()
        .statusCode(400)
        .body(equalTo("Expected SourceNodeData but got: Sink"))
    }

    "return 404 for not existent scenario" in {
      val nonExistingScenarioName = "nonExistingScenario"

      given()
        .when()
        .basicAuthAllPermUser()
        .jsonBody(
          s"""{
             |  "processProperties": {
             |    "additionalFields": {
             |      "properties": {
             |        "parallelism": "1",
             |        "spillStateToDisk": "true",
             |        "useAsyncInterpretation": "",
             |        "checkpointIntervalInSeconds": ""
             |      },
             |      "metaDataType": "StreamMetaData"
             |    }
             |  },
             |  "nodeData": {
             |    "id": "sourceId",
             |    "type": "Source",
             |    "ref": {
             |      "typ": "barSource",
             |      "parameters": []
             |    }
             |  }
             |}""".stripMargin
        )
        .post(s"$nuDesignerHttpAddress/api/nodes/$nonExistingScenarioName/records?limit=10")
        .Then()
        .statusCode(404)
        .body(equalTo(s"No scenario $nonExistingScenarioName found"))
    }
  }

  private lazy val exampleScenario = ScenarioBuilder
    .streaming("test")
    .source("sourceId", "barSource")
    .emptySink("sinkId", "barSink")

  private lazy val exampleNodesAdditionalInfoRequestBody =
    s"""{
       |  "id": "1",
       |  "service": {
       |    "id": "otherService",
       |    "parameters": []
       |  },
       |  "output": "out",
       |  "additionalFields": null,
       |  "type": "Enricher"
       |}""".stripMargin

  private def exampleNodeValidationRequestForFragment(fragmentName: String): String =
    s"""{
       |    "outgoingEdges": [
       |        {
       |            "from": "$fragmentName",
       |            "to": "variable",
       |            "edgeType": {
       |                "name": "output",
       |                "type": "FragmentOutput"
       |            }
       |        }
       |    ],
       |    "nodeData": {
       |        "additionalFields": {
       |            "layoutData": {
       |                "x": 175,
       |                "y": 180
       |            }
       |        },
       |        "ref": {
       |            "id": "$fragmentName",
       |            "parameters": [],
       |            "outputVariableNames": {
       |                "output": "output"
       |            }
       |        },
       |        "isDisabled": null,
       |        "fragmentParams": null,
       |        "type": "FragmentInput",
       |        "branchParametersTemplate": [],
       |        "id": "fragment"
       |    },
       |    "processProperties": {
       |        "isFragment": false,
       |        "additionalFields": {
       |            "description": null,
       |            "properties": {
       |                "parallelism": "1",
       |                "checkpointIntervalInSeconds": "",
       |                "maxEvents": "1",
       |                "numberOfThreads": "1",
       |                "spillStateToDisk": "true",
       |                "environment": "test",
       |                "useAsyncInterpretation": ""
       |            },
       |            "metaDataType": "StreamMetaData"
       |        }
       |    },
       |    "branchVariableTypes": {},
       |    "variableTypes": {}
       |}""".stripMargin

  private lazy val exampleParametersValidationRequestBody =
    s"""{
       |    "parameters": [
       |        {
       |            "name": "condition",
       |            "typ": {
       |                "display": "Boolean",
       |                "type": "TypedClass",
       |                "refClazzName": "java.lang.Boolean",
       |                "params": []
       |            },
       |            "expression": {
       |                "language": "spel",
       |                "expression": "#input.amount > 2"
       |            }
       |        }
       |    ],
       |    "variableTypes": {
       |        "input": {
       |            "display": "Record{amount: Long(5)}",
       |            "type": "TypedObjectTypingResult",
       |            "fields": {
       |                "amount": {
       |                    "value": 5,
       |                    "display": "Long(5)",
       |                    "type": "TypedObjectWithValue",
       |                    "refClazzName": "java.lang.Long",
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
       |                    "value": 5,
       |                    "display": "Long(5)",
       |                    "type": "TypedObjectWithValue",
       |                    "refClazzName": "java.lang.Long",
       |                    "params": []
       |                }
       |            ]
       |        }
       |    }
       |}""".stripMargin

  private lazy val sourceTestingScenarioSourceId = "sourceId"

  private lazy val sourceTestingScenario = ScenarioBuilder
    .streaming("source_testing_scenario")
    .source(sourceTestingScenarioSourceId, "genericSourceWithCustomTestingSupport", "elements" -> "{'test'}".spel)
    .emptySink("sinkId", "barSink")

}
