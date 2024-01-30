package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.given
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers.equalTo
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.test.{
  NuRestAssureExtensions,
  NuRestAssureMatchers,
  PatientScalaFutures,
  RestAssuredVerboseLogging
}
import pl.touk.nussknacker.ui.api.helpers._

class NodesApiSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithMockableDeploymentManager
    with NuTestScenarioManager
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging
    with PatientScalaFutures {

  private val exampleScenario = ScenarioBuilder
    .streaming("test")
    .source("sourceId", "barSource")
    .emptySink("sinkId", "barSink")

  "The endpoint for nodes" - {
    "additional info when" - {
      "authenticated should" - {
        "return additional info for node with expression in existing scenario" in {
          given()
            .applicationState {
              createSavedScenario(exampleScenario)
            }
            .basicAuth("allpermuser", "allpermuser")
            .jsonBody(
              s"""{
                 |    "id": "enricher",
                 |    "service": {
                 |        "id": "paramService",
                 |        "parameters": [
                 |            {
                 |                "name": "id",
                 |                "expression": {
                 |                    "language": "spel",
                 |                    "expression": "'a'"
                 |                }
                 |            }
                 |        ]
                 |    },
                 |    "output": "out",
                 |    "additionalFields": null,
                 |    "type": "Enricher"
                 |}""".stripMargin
            )
            .when()
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
            .basicAuth("allpermuser", "allpermuser")
            .jsonBody(
              s"""{
                 |    "id": "enricher",
                 |    "service": {
                 |        "id": "paramService",
                 |        "parameters": []
                 |    },
                 |    "output": "out",
                 |    "additionalFields": null,
                 |    "type": "Enricher"
                 |}""".stripMargin
            )
            .when()
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
            .basicAuth("allpermuser", "allpermuser")
            .jsonBody(exampleNodesAdditionalInfoRequestBody)
            .when()
            .post(s"$nuDesignerHttpAddress/api/nodes/$nonExistingScenarioName/additionalInfo")
            .Then()
            .statusCode(404)
            .body(
              equalTo(s"No scenario $nonExistingScenarioName found")
            )
        }
      }
      "not authenticated should" - {
        "forbid access" in {
          given()
            .applicationState {
              createSavedScenario(exampleScenario)
            }
            .noAuth()
            .jsonBody(exampleNodesAdditionalInfoRequestBody)
            .when()
            .post(s"$nuDesignerHttpAddress/api/nodes/${exampleScenario.name}/additionalInfo")
            .Then()
            .statusCode(401)
            .body(
              equalTo("The resource requires authentication, which was not supplied with the request")
            )
        }
      }
    }
    "validation when" - {
      "authenticated should" - {
        "validate correct node without errors" in {
          given()
            .applicationState {
              createSavedScenario(exampleScenario)
            }
            .basicAuth("allpermuser", "allpermuser")
            .jsonBody(
              s"""{
                 |    "nodeData": {
                 |        "id": "id",
                 |        "expression": {
                 |            "language": "spel",
                 |            "expression": "#longValue > 1"
                 |        },
                 |        "isDisabled": null,
                 |        "additionalFields": null,
                 |        "type": "Filter"
                 |    },
                 |    "processProperties": {
                 |        "isFragment": false,
                 |        "additionalFields": {
                 |            "description": null,
                 |            "properties": {
                 |                "parallelism": "",
                 |                "spillStateToDisk": "true",
                 |                "useAsyncInterpretation": "",
                 |                "checkpointIntervalInSeconds": ""
                 |            },
                 |            "metaDataType": "StreamMetaData"
                 |        }
                 |    },
                 |    "variableTypes": {
                 |        "existButString": {
                 |            "display": "String",
                 |            "type": "TypedClass",
                 |            "refClazzName": "java.lang.String",
                 |            "params": []
                 |        },
                 |        "longValue": {
                 |            "display": "Long",
                 |            "type": "TypedClass",
                 |            "refClazzName": "java.lang.Long",
                 |            "params": []
                 |        }
                 |    },
                 |    "branchVariableTypes": null,
                 |    "outgoingEdges": null
                 |}""".stripMargin
            )
            .when()
            .post(s"$nuDesignerHttpAddress/api/nodes/${exampleScenario.name}/validation")
            .Then()
            .statusCode(200)
            .equalsJsonBody(
              s"""{
                 |    "parameters": null,
                 |    "expressionType": {
                 |        "display": "Boolean",
                 |        "type": "TypedClass",
                 |        "refClazzName": "java.lang.Boolean",
                 |        "params": []
                 |    },
                 |    "validationErrors": [],
                 |    "validationPerformed": true
                 |}""".stripMargin
            )
        }
        "validate filter node when wrong parameter type is given" in {
          given()
            .applicationState {
              createSavedScenario(exampleScenario)
            }
            .basicAuth("allpermuser", "allpermuser")
            .jsonBody(
              s"""{
                 |    "nodeData": {
                 |        "id": "id",
                 |        "expression": {
                 |            "language": "spel",
                 |            "expression": "#existButString"
                 |        },
                 |        "isDisabled": null,
                 |        "additionalFields": null,
                 |        "type": "Filter"
                 |    },
                 |    "processProperties": {
                 |        "isFragment": false,
                 |        "additionalFields": {
                 |            "description": null,
                 |            "properties": {
                 |                "parallelism": "",
                 |                "spillStateToDisk": "true",
                 |                "useAsyncInterpretation": "",
                 |                "checkpointIntervalInSeconds": ""
                 |            },
                 |            "metaDataType": "StreamMetaData"
                 |        }
                 |    },
                 |    "variableTypes": {
                 |        "existButString": {
                 |            "display": "String",
                 |            "type": "TypedClass",
                 |            "refClazzName": "java.lang.String",
                 |            "params": []
                 |        },
                 |        "longValue": {
                 |            "display": "Long",
                 |            "type": "TypedClass",
                 |            "refClazzName": "java.lang.Long",
                 |            "params": []
                 |        }
                 |    },
                 |    "branchVariableTypes": null,
                 |    "outgoingEdges": null
                 |}""".stripMargin
            )
            .when()
            .post(s"$nuDesignerHttpAddress/api/nodes/${exampleScenario.name}/validation")
            .Then()
            .statusCode(200)
            .equalsJsonBody(
              s"""{
                 |    "parameters": null,
                 |    "expressionType": {
                 |        "display": "Unknown",
                 |        "type": "Unknown",
                 |        "refClazzName": "java.lang.Object",
                 |        "params": []
                 |    },
                 |    "validationErrors": [
                 |        {
                 |            "typ": "ExpressionParserCompilationError",
                 |            "message": "Failed to parse expression: Bad expression type, expected: Boolean, found: String",
                 |            "description": "There is problem with expression in field Some($$expression) - it could not be parsed.",
                 |            "fieldName": "$$expression",
                 |            "errorType": "SaveAllowed"
                 |        }
                 |    ],
                 |    "validationPerformed": true
                 |}""".stripMargin
            )
        }
        "validate incorrect sink expression" in {
          given()
            .applicationState {
              createSavedScenario(exampleScenario)
            }
            .basicAuth("allpermuser", "allpermuser")
            .jsonBody(
              s"""{
                 |    "nodeData": {
                 |        "id": "mysink",
                 |        "ref": {
                 |            "typ": "kafka-string",
                 |            "parameters": [
                 |                {
                 |                    "name": "Value",
                 |                    "expression": {
                 |                        "language": "spel",
                 |                        "expression": "notvalidspelexpression"
                 |                    }
                 |                },
                 |                {
                 |                    "name": "Topic",
                 |                    "expression": {
                 |                        "language": "spel",
                 |                        "expression": "'test-topic'"
                 |                    }
                 |                }
                 |            ]
                 |        },
                 |        "endResult": null,
                 |        "isDisabled": null,
                 |        "additionalFields": null,
                 |        "type": "Sink"
                 |    },
                 |    "processProperties": {
                 |        "isFragment": false,
                 |        "additionalFields": {
                 |            "description": null,
                 |            "properties": {
                 |                "parallelism": "",
                 |                "spillStateToDisk": "true",
                 |                "useAsyncInterpretation": "",
                 |                "checkpointIntervalInSeconds": ""
                 |            },
                 |            "metaDataType": "StreamMetaData"
                 |        }
                 |    },
                 |    "variableTypes": {
                 |        "existButString": {
                 |            "display": "String",
                 |            "type": "TypedClass",
                 |            "refClazzName": "java.lang.String",
                 |            "params": []
                 |        },
                 |        "longValue": {
                 |            "display": "Long",
                 |            "type": "TypedClass",
                 |            "refClazzName": "java.lang.Long",
                 |            "params": []
                 |        }
                 |    },
                 |    "branchVariableTypes": null,
                 |    "outgoingEdges": null
                 |}""".stripMargin
            )
            .when()
            .post(s"$nuDesignerHttpAddress/api/nodes/${exampleScenario.name}/validation")
            .Then()
            .statusCode(200)
            .equalsJsonBody(
              s"""{
                 |    "parameters": [
                 |        {
                 |            "name": "Topic",
                 |            "typ": {
                 |                "display": "String",
                 |                "type": "TypedClass",
                 |                "refClazzName": "java.lang.String",
                 |                "params": []
                 |            },
                 |            "editor": {
                 |                "simpleEditor": {
                 |                    "type": "StringParameterEditor"
                 |                },
                 |                "defaultMode": "RAW",
                 |                "type": "DualParameterEditor"
                 |            },
                 |            "defaultValue": {
                 |                "language": "spel",
                 |                "expression": "''"
                 |            },
                 |            "additionalVariables": {},
                 |            "variablesToHide": [],
                 |            "branchParam": false,
                 |            "hintText": null,
                 |            "label": "Topic"
                 |        },
                 |        {
                 |            "name": "Value",
                 |            "typ": {
                 |                "display": "Unknown",
                 |                "type": "Unknown",
                 |                "refClazzName": "java.lang.Object",
                 |                "params": []
                 |            },
                 |            "editor": {
                 |                "type": "RawParameterEditor"
                 |            },
                 |            "defaultValue": {
                 |                "language": "spel",
                 |                "expression": ""
                 |            },
                 |            "additionalVariables": {},
                 |            "variablesToHide": [],
                 |            "branchParam": false,
                 |            "hintText": null,
                 |            "label": "Value"
                 |        }
                 |    ],
                 |    "expressionType": null,
                 |    "validationErrors": [
                 |        {
                 |            "typ": "ExpressionParserCompilationError",
                 |            "message": "Failed to parse expression: Non reference 'notvalidspelexpression' occurred. Maybe you missed '#' in front of it?",
                 |            "description": "There is problem with expression in field Some(Value) - it could not be parsed.",
                 |            "fieldName": "Value",
                 |            "errorType": "SaveAllowed"
                 |        }
                 |    ],
                 |    "validationPerformed": true
                 |}""".stripMargin
            )
        }
        "validate node using dictionaries" in {
          given()
            .applicationState {
              createSavedScenario(exampleScenario)
            }
            .basicAuth("allpermuser", "allpermuser")
            .jsonBody(
              s"""{
                 |    "nodeData": {
                 |        "id": "id",
                 |        "expression": {
                 |            "language": "spel",
                 |            "expression": "#DICT.Bar != #DICT.Foo"
                 |        },
                 |        "isDisabled": null,
                 |        "additionalFields": null,
                 |        "type": "Filter"
                 |    },
                 |    "processProperties": {
                 |        "isFragment": false,
                 |        "additionalFields": {
                 |            "description": null,
                 |            "properties": {
                 |                "parallelism": "",
                 |                "spillStateToDisk": "true",
                 |                "useAsyncInterpretation": "",
                 |                "checkpointIntervalInSeconds": ""
                 |            },
                 |            "metaDataType": "StreamMetaData"
                 |        }
                 |    },
                 |    "variableTypes": {},
                 |    "branchVariableTypes": null,
                 |    "outgoingEdges": null
                 |}""".stripMargin
            )
            .when()
            .post(s"$nuDesignerHttpAddress/api/nodes/${exampleScenario.name}/validation")
            .Then()
            .statusCode(200)
            .equalsJsonBody(s"""{
                 |    "parameters": null,
                 |    "expressionType": {
                 |        "display": "Boolean",
                 |        "type": "TypedClass",
                 |        "refClazzName": "java.lang.Boolean",
                 |        "params": []
                 |    },
                 |    "validationErrors": [],
                 |    "validationPerformed": true
                 |}""".stripMargin)
        }
        "validate node id" in {
          given()
            .applicationState {
              createSavedScenario(exampleScenario)
            }
            .basicAuth("allpermuser", "allpermuser")
            .jsonBody(
              s"""{
                 |    "nodeData": {
                 |        "id": " ",
                 |        "expression": {
                 |            "language": "spel",
                 |            "expression": "true"
                 |        },
                 |        "isDisabled": null,
                 |        "additionalFields": null,
                 |        "type": "Filter"
                 |    },
                 |    "processProperties": {
                 |        "isFragment": false,
                 |        "additionalFields": {
                 |            "description": null,
                 |            "properties": {
                 |                "parallelism": "",
                 |                "spillStateToDisk": "true",
                 |                "useAsyncInterpretation": "",
                 |                "checkpointIntervalInSeconds": ""
                 |            },
                 |            "metaDataType": "StreamMetaData"
                 |        }
                 |    },
                 |    "variableTypes": {},
                 |    "branchVariableTypes": null,
                 |    "outgoingEdges": null
                 |}""".stripMargin
            )
            .when()
            .post(s"$nuDesignerHttpAddress/api/nodes/${exampleScenario.name}/validation")
            .Then()
            .statusCode(200)
            .equalsJsonBody(s"""{
                 |    "parameters": null,
                 |    "expressionType": {
                 |        "value": true,
                 |        "display": "Boolean(true)",
                 |        "type": "TypedObjectWithValue",
                 |        "refClazzName": "java.lang.Boolean",
                 |        "params": []
                 |    },
                 |    "validationErrors": [
                 |        {
                 |            "typ": "NodeIdValidationError",
                 |            "message": "Node name cannot be blank",
                 |            "description": "Blank node name",
                 |            "fieldName": "$$id",
                 |            "errorType": "SaveAllowed"
                 |        }
                 |    ],
                 |    "validationPerformed": true
                 |}""".stripMargin)
        }
      }
      "not authenticated should" - {
        "forbid access" in {
          given()
            .applicationState {
              createSavedScenario(exampleScenario)
            }
            .noAuth()
            .jsonBody(exampleNodesValidationRequestBody)
            .when()
            .post(s"$nuDesignerHttpAddress/api/nodes/${exampleScenario.name}/validation")
            .Then()
            .statusCode(401)
            .body(
              equalTo("The resource requires authentication, which was not supplied with the request")
            )
        }
      }
    }
  }

  "The endpoint for properties" - {
    "additional info when" - {
      "authenticated should" - {
        "return additional info for scenario properties" in {
          given()
            .applicationState {
              createSavedScenario(exampleScenario)
            }
            .basicAuth("allpermuser", "allpermuser")
            .jsonBody(
              s"""{
                 |    "isFragment": false,
                 |    "additionalFields": {
                 |        "description": null,
                 |        "properties": {
                 |            "parallelism": "",
                 |            "checkpointIntervalInSeconds": "",
                 |            "numberOfThreads": "2",
                 |            "spillStateToDisk": "true",
                 |            "environment": "test",
                 |            "useAsyncInterpretation": ""
                 |        },
                 |        "metaDataType": "StreamMetaData"
                 |    }
                 |}""".stripMargin
            )
            .when()
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
      "not authenticated should" - {
        "forbid access" in {
          given()
            .applicationState {
              createSavedScenario(exampleScenario)
            }
            .jsonBody(examplePropertiesAdditionalInfoRequestBody)
            .noAuth()
            .when()
            .post(s"$nuDesignerHttpAddress/api/properties/${exampleScenario.name}/additionalInfo")
            .Then()
            .statusCode(401)
            .body(
              equalTo("The resource requires authentication, which was not supplied with the request")
            )
        }
      }
    }
    "validation when" - {
      "authenticated should" - {
        "validate properties" in {
          given()
            .applicationState {
              createSavedScenario(exampleScenario)
            }
            .basicAuth("allpermuser", "allpermuser")
            .jsonBody(
              s"""{
                 |    "additionalFields": {
                 |        "description": null,
                 |        "properties": {
                 |            "parallelism": "",
                 |            "checkpointIntervalInSeconds": "",
                 |            "numberOfThreads": "a",
                 |            "spillStateToDisk": "true",
                 |            "environment": "test",
                 |            "useAsyncInterpretation": ""
                 |        },
                 |        "metaDataType": "StreamMetaData"
                 |    },
                 |    "name": "test"
                 |}""".stripMargin
            )
            .post(s"$nuDesignerHttpAddress/api/properties/${exampleScenario.name}/validation")
            .Then()
            .statusCode(200)
            .equalsJsonBody(s"""{
                 |    "parameters": null,
                 |    "expressionType": null,
                 |    "validationErrors": [
                 |        {
                 |            "typ": "InvalidPropertyFixedValue",
                 |            "message": "Property numberOfThreads (Number of threads) has invalid value",
                 |            "description": "Expected one of 1, 2, got: a.",
                 |            "fieldName": "numberOfThreads",
                 |            "errorType": "SaveAllowed"
                 |        },
                 |        {
                 |            "typ": "UnknownProperty",
                 |            "message": "Unknown property parallelism",
                 |            "description": "Property parallelism is not known",
                 |            "fieldName": "parallelism",
                 |            "errorType": "SaveAllowed"
                 |        },
                 |        {
                 |            "typ": "UnknownProperty",
                 |            "message": "Unknown property checkpointIntervalInSeconds",
                 |            "description": "Property checkpointIntervalInSeconds is not known",
                 |            "fieldName": "checkpointIntervalInSeconds",
                 |            "errorType": "SaveAllowed"
                 |        },
                 |        {
                 |            "typ": "UnknownProperty",
                 |            "message": "Unknown property spillStateToDisk",
                 |            "description": "Property spillStateToDisk is not known",
                 |            "fieldName": "spillStateToDisk",
                 |            "errorType": "SaveAllowed"
                 |        },
                 |        {
                 |            "typ": "UnknownProperty",
                 |            "message": "Unknown property useAsyncInterpretation",
                 |            "description": "Property useAsyncInterpretation is not known",
                 |            "fieldName": "useAsyncInterpretation",
                 |            "errorType": "SaveAllowed"
                 |        }
                 |    ],
                 |    "validationPerformed": true
                 |}""".stripMargin)
        }
        "validate scenario id" in {
          given()
            .applicationState {
              createSavedScenario(exampleScenario)
            }
            .basicAuth("allpermuser", "allpermuser")
            .jsonBody(
              s"""{
                 |    "additionalFields": {
                 |        "description": null,
                 |        "properties": {
                 |            "parallelism": "",
                 |            "checkpointIntervalInSeconds": "",
                 |            "numberOfThreads": "1",
                 |            "spillStateToDisk": "true",
                 |            "environment": "test",
                 |            "useAsyncInterpretation": "1"
                 |        },
                 |        "metaDataType": "StreamMetaData"
                 |    },
                 |    "name": " "
                 |}""".stripMargin
            )
            .post(s"$nuDesignerHttpAddress/api/properties/${exampleScenario.name}/validation")
            .Then()
            .statusCode(200)
            .equalsJsonBody(s"""{
                   |    "parameters": null,
                   |    "expressionType": null,
                   |    "validationErrors": [
                   |        {
                   |            "typ": "ScenarioNameError",
                   |            "message": "Scenario name cannot be blank",
                   |            "description": "Blank scenario name",
                   |            "fieldName": "$$id",
                   |            "errorType": "SaveAllowed"
                   |        },
                   |        {
                   |            "typ": "UnknownProperty",
                   |            "message": "Unknown property parallelism",
                   |            "description": "Property parallelism is not known",
                   |            "fieldName": "parallelism",
                   |            "errorType": "SaveAllowed"
                   |        },
                   |        {
                   |            "typ": "UnknownProperty",
                   |            "message": "Unknown property checkpointIntervalInSeconds",
                   |            "description": "Property checkpointIntervalInSeconds is not known",
                   |            "fieldName": "checkpointIntervalInSeconds",
                   |            "errorType": "SaveAllowed"
                   |        },
                   |        {
                   |            "typ": "UnknownProperty",
                   |            "message": "Unknown property spillStateToDisk",
                   |            "description": "Property spillStateToDisk is not known",
                   |            "fieldName": "spillStateToDisk",
                   |            "errorType": "SaveAllowed"
                   |        },
                   |        {
                   |            "typ": "UnknownProperty",
                   |            "message": "Unknown property useAsyncInterpretation",
                   |            "description": "Property useAsyncInterpretation is not known",
                   |            "fieldName": "useAsyncInterpretation",
                   |            "errorType": "SaveAllowed"
                   |        },
                   |        {
                   |            "typ": "ScenarioNameValidationError",
                   |            "message": "Invalid scenario name  . Only digits, letters, underscore (_), hyphen (-) and space in the middle are allowed",
                   |            "description": "Provided scenario name is invalid for this category. Please enter valid name using only specified characters.",
                   |            "fieldName": "$$id",
                   |            "errorType": "SaveAllowed"
                   |        }
                   |    ],
                   |    "validationPerformed": true
                   |}""".stripMargin)
        }
      }
      "not authenticated should" - {
        "forbid access" in {
          given
            .applicationState {
              createSavedScenario(exampleScenario)
            }
            .jsonBody(examplePropertiesValidationRequestBody)
            .noAuth()
            .post(s"$nuDesignerHttpAddress/api/properties/${exampleScenario.name}/validation")
            .Then()
            .statusCode(401)
            .body(
              equalTo("The resource requires authentication, which was not supplied with the request")
            )
        }
      }
    }
  }

  "The endpoint for parameters" - {
    "validation when" - {
      "authenticated should" - {
        "validate correct parameter" in {
          given()
            .basicAuth("allpermuser", "allpermuser")
            .jsonBody(
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
            )
            .when()
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
            .basicAuth("allpermuser", "allpermuser")
            .jsonBody(
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
                 |                "expression": "#input.amount"
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
            )
            .when()
            .post(s"$nuDesignerHttpAddress/api/parameters/streaming/validate")
            .Then()
            .statusCode(200)
            .equalsJsonBody(s"""{
                 |  "validationErrors": [ {
                 |    "typ": "ExpressionParserCompilationError",
                 |    "message": "Failed to parse expression: Bad expression type, expected: Boolean, found: Long(5)",
                 |    "description": "There is problem with expression in field Some(condition) - it could not be parsed.",
                 |    "fieldName": "condition",
                 |    "errorType": "SaveAllowed"
                 |  } ],
                 |  "validationPerformed": true
                 |}""".stripMargin)
        }
      }
      "not authenticated should" - {
        "forbid access" in {
          given()
            .jsonBody(exampleParametersValidationRequestBody)
            .noAuth()
            .when()
            .post(s"$nuDesignerHttpAddress/api/parameters/streaming/validate")
            .Then()
            .statusCode(401)
            .body(
              equalTo("The resource requires authentication, which was not supplied with the request")
            )
        }
      }
    }
    "suggestions when" - {
      "authenticated should" - {
        "suggest the name of parameter" in {
          given()
            .basicAuth("allpermuser", "allpermuser")
            .jsonBody(
              s"""{
                 |    "expression": {
                 |        "language": "spel",
                 |        "expression": "#inpu"
                 |    },
                 |    "caretPosition2d": {
                 |        "row": 0,
                 |        "column": 5
                 |    },
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
            )
            .when()
            .post(s"$nuDesignerHttpAddress/api/parameters/streaming/suggestions")
            .Then()
            .statusCode(200)
            .body("methodName[0]", equalTo("#input"))
        }
        "not suggest anything if no such parameters exist" in {
          given()
            .basicAuth("allpermuser", "allpermuser")
            .jsonBody(
              s"""{
                 |    "expression": {
                 |        "language": "spel",
                 |        "expression": "#inpu"
                 |    },
                 |    "caretPosition2d": {
                 |        "row": 0,
                 |        "column": 5
                 |    },
                 |    "variableTypes": {}
                 |}""".stripMargin
            )
            .when()
            .post(s"$nuDesignerHttpAddress/api/parameters/streaming/suggestions")
            .Then()
            .statusCode(200)
            .body(equalTo("[]"))
        }
      }
      "not authenticated should" - {
        "forbid access" in {
          given()
            .jsonBody(exampleParametersSuggestionsRequestBody)
            .noAuth()
            .when()
            .post(s"$nuDesignerHttpAddress/api/parameters/streaming/suggestions")
            .Then()
            .statusCode(401)
            .body(
              equalTo("The resource requires authentication, which was not supplied with the request")
            )
        }
      }
    }
  }

  private lazy val exampleNodesAdditionalInfoRequestBody =
    s"""{
       |    "id": "1",
       |    "service": {
       |        "id": "otherService",
       |        "parameters": []
       |    },
       |    "output": "out",
       |    "additionalFields": null,
       |    "type": "Enricher"
       |}""".stripMargin

  private lazy val exampleNodesValidationRequestBody =
    s"""{
       |    "nodeData": {
       |        "id": "id",
       |        "expression": {
       |            "language": "spel",
       |            "expression": "#existButString"
       |        },
       |        "isDisabled": null,
       |        "additionalFields": null,
       |        "type": "Filter"
       |    },
       |    "processProperties": {
       |        "isFragment": false,
       |        "additionalFields": {
       |            "description": null,
       |            "properties": {
       |                "parallelism": "",
       |                "spillStateToDisk": "true",
       |                "useAsyncInterpretation": "",
       |                "checkpointIntervalInSeconds": ""
       |            },
       |            "metaDataType": "StreamMetaData"
       |        }
       |    },
       |    "variableTypes": {
       |        "existButString": {
       |            "display": "String",
       |            "type": "TypedClass",
       |            "refClazzName": "java.lang.String",
       |            "params": []
       |        },
       |        "longValue": {
       |            "display": "Long",
       |            "type": "TypedClass",
       |            "refClazzName": "java.lang.Long",
       |            "params": []
       |        }
       |    },
       |    "branchVariableTypes": null,
       |    "outgoingEdges": null
       |}""".stripMargin

  private lazy val examplePropertiesAdditionalInfoRequestBody =
    s"""{
       |    "isFragment": false,
       |    "additionalFields": {
       |        "description": null,
       |        "properties": {
       |            "parallelism": "",
       |            "checkpointIntervalInSeconds": "",
       |            "numberOfThreads": "2",
       |            "spillStateToDisk": "true",
       |            "environment": "test",
       |            "useAsyncInterpretation": ""
       |        },
       |        "metaDataType": "StreamMetaData"
       |    }
       |}""".stripMargin

  private lazy val examplePropertiesValidationRequestBody =
    s"""{
       |    "additionalFields": {
       |        "description": null,
       |        "properties": {
       |            "parallelism": "",
       |            "checkpointIntervalInSeconds": "",
       |            "numberOfThreads": "a",
       |            "spillStateToDisk": "true",
       |            "environment": "test",
       |            "useAsyncInterpretation": ""
       |        },
       |        "metaDataType": "StreamMetaData"
       |    },
       |    "id": "test"
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
       |""".stripMargin

  private lazy val exampleParametersSuggestionsRequestBody =
    s"""{
       |    "expression": {
       |        "language": "spel",
       |        "expression": "#inpu"
       |    },
       |    "caretPosition2d": {
       |        "row": 0,
       |        "column": 5
       |    },
       |    "variableTypes": {}
       |}""".stripMargin

}
