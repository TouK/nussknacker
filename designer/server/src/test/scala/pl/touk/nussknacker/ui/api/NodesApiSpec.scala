package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.given
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers.equalTo
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
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
    with NuScenarioConfigurationHelper
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging
    with PatientScalaFutures {

  val processName: ProcessName = ProcessName("test")

  val process: CanonicalProcess = ScenarioBuilder
    .streaming(processName.value)
    .source("sourceId", "barSource")
    .emptySink("sinkId", "barSink")

  "The endpoint for nodes" - {
    "additional info when" - {
      "authenticated should" - {
        "return additional info for existing process" in {
          createSavedProcess(process, TestCategories.Category1, TestProcessingTypes.Streaming)

          given()
            .basicAuth("allpermuser", "allpermuser")
            .and()
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
            .post(s"$nuDesignerHttpAddress/api/nodes/${process.name}/additionalInfo")
            .Then()
            .statusCode(200)
            .body(
              equalsJson(s"""{
                   |  "content": "\\nSamples:\\n\\n| id  | value |\\n| --- | ----- |\\n| a   | generated |\\n| b   | not existent |\\n\\nResults for a can be found [here](http://touk.pl?id=a)\\n",
                   |  "type": "MarkdownAdditionalInfo"
                   |}""".stripMargin)
            )

          given()
            .basicAuth("allpermuser", "allpermuser")
            .and()
            .jsonBody(
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
            )
            .when()
            .post(s"$nuDesignerHttpAddress/api/nodes/${process.name}/additionalInfo")
            .Then()
            .statusCode(200)
            .body(
              equalTo("")
            )
        }
        "return 404 for not existent process" in {
          val wrongName: String = "wrongProcessName"

          given()
            .basicAuth("allpermuser", "allpermuser")
            .and()
            .jsonBody(
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
            )
            .when()
            .post(s"$nuDesignerHttpAddress/api/nodes/$wrongName/additionalInfo")
            .Then()
            .statusCode(404)
            .body(
              equalTo(s"No scenario $wrongName found")
            )
        }
      }
      "not authenticated should" - {
        "forbid access" in {
          createSavedProcess(process, TestCategories.Category1, TestProcessingTypes.Streaming)

          given()
            .auth()
            .none
            .and()
            .jsonBody(s"""{
                 |    "id": "1",
                 |    "service": {
                 |        "id": "otherService",
                 |        "parameters": []
                 |    },
                 |    "output": "out",
                 |    "additionalFields": null,
                 |    "type": "Enricher"
                 |}""".stripMargin)
            .when()
            .post(s"$nuDesignerHttpAddress/api/nodes/${process.name}/additionalInfo")
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
          createSavedProcess(process, TestCategories.Category1, TestProcessingTypes.Streaming)

          given()
            .basicAuth("allpermuser", "allpermuser")
            .and()
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
            .post(s"$nuDesignerHttpAddress/api/nodes/${process.name}/validation")
            .Then()
            .statusCode(200)
            .body(
              equalsJson(s"""{
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
            )
        }
        "validate filter node when wrong parameter type is given" in {
          createSavedProcess(process, TestCategories.Category1, TestProcessingTypes.Streaming)

          given()
            .basicAuth("allpermuser", "allpermuser")
            .and()
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
            .post(s"$nuDesignerHttpAddress/api/nodes/${process.name}/validation")
            .Then()
            .statusCode(200)
            .body(
              equalsJson(
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
            )
        }
        "validate incorrect sink expression" in {
          createSavedProcess(process, TestCategories.Category1, TestProcessingTypes.Streaming)

          given()
            .basicAuth("allpermuser", "allpermuser")
            .and()
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
            .post(s"$nuDesignerHttpAddress/api/nodes/${process.name}/validation")
            .Then()
            .statusCode(200)
            .body("validationErrors[0].typ", equalTo("ExpressionParserCompilationError"))
            .body(
              "validationErrors[0].message",
              equalTo(
                "Failed to parse expression: Non reference 'notvalidspelexpression' occurred. Maybe you missed '#' in front of it?"
              )
            )
            .body("validationErrors[0].fieldName", equalTo("Value"))
        }
        "validate node using dictionaries" in {
          createSavedProcess(process, TestCategories.Category1, TestProcessingTypes.Streaming)

          given()
            .basicAuth("allpermuser", "allpermuser")
            .and()
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
            .post(s"$nuDesignerHttpAddress/api/nodes/${process.name}/validation")
            .Then()
            .statusCode(200)
            .body(equalsJson(s"""{
                 |    "parameters": null,
                 |    "expressionType": {
                 |        "display": "Boolean",
                 |        "type": "TypedClass",
                 |        "refClazzName": "java.lang.Boolean",
                 |        "params": []
                 |    },
                 |    "validationErrors": [],
                 |    "validationPerformed": true
                 |}""".stripMargin))
        }
        "validate node id" in {
          createSavedProcess(process, TestCategories.Category1, TestProcessingTypes.Streaming)

          given()
            .basicAuth("allpermuser", "allpermuser")
            .and()
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
            .post(s"$nuDesignerHttpAddress/api/nodes/${process.name}/validation")
            .Then()
            .statusCode(200)
            .body(equalsJson(s"""{
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
                 |}""".stripMargin))
        }
      }
      "not authenticated should" - {
        "forbid access" in {
          createSavedProcess(process, TestCategories.Category1, TestProcessingTypes.Streaming)

          given()
            .auth()
            .none()
            .and()
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
            .post(s"$nuDesignerHttpAddress/api/nodes/${process.name}/validation")
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
        "return additional info for process properties" in {
          createSavedProcess(process, TestCategories.Category1, TestProcessingTypes.Streaming)

          given()
            .basicAuth("allpermuser", "allpermuser")
            .and()
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
            .post(s"$nuDesignerHttpAddress/api/properties/${process.name}/additionalInfo")
            .Then()
            .statusCode(200)
            .body(
              equalsJson(s"""{
                   |  "content": "2 threads will be used on environment 'test'",
                   |  "type": "MarkdownAdditionalInfo"
                   |}""".stripMargin)
            )
        }
      }
      "not authenticated should" - {
        "forbid access" in {
          createSavedProcess(process, TestCategories.Category1, TestProcessingTypes.Streaming)

          given()
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
            .and()
            .auth()
            .none()
            .when()
            .post(s"$nuDesignerHttpAddress/api/properties/${process.name}/additionalInfo")
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
          createSavedProcess(process, TestCategories.Category1, TestProcessingTypes.Streaming)

          given()
            .basicAuth("allpermuser", "allpermuser")
            .and()
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
            .post(s"$nuDesignerHttpAddress/api/properties/${process.name}/validation")
            .Then()
            .statusCode(200)
            .body("validationErrors[0].typ", equalTo("InvalidPropertyFixedValue"))
            .body(
              "validationErrors[0].message",
              equalTo("Property numberOfThreads (Number of threads) has invalid value")
            )
            .body("validationErrors[0].description", equalTo("Expected one of 1, 2, got: a."))
            .body("validationErrors[0].fieldName", equalTo("numberOfThreads"))
            .body("validationPerformed", equalTo(true))
        }
        "validate scenario id" in {
          createSavedProcess(process, TestCategories.Category1, TestProcessingTypes.Streaming)

          given()
            .basicAuth("allpermuser", "allpermuser")
            .and()
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
            .post(s"$nuDesignerHttpAddress/api/properties/${process.name}/validation")
            .Then()
            .statusCode(200)
            .body(
              equalsJson(s"""{
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
            )
        }
      }
      "not authenticated should" - {
        "forbid access" in {
          createSavedProcess(process, TestCategories.Category1, TestProcessingTypes.Streaming)

          given
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
                 |    "id": "test"
                 |}""".stripMargin
            )
            .and()
            .auth()
            .none()
            .post(s"$nuDesignerHttpAddress/api/properties/${process.name}/validation")
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
            .and
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
            .body(
              equalsJson(s"""{
                   |  "validationErrors": [],
                   |  "validationPerformed": true
                   |}""".stripMargin)
            )
        }
        "validate incorrect parameter" in {
          given()
            .basicAuth("allpermuser", "allpermuser")
            .and()
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
            .body(equalsJson(s"""{
                 |  "validationErrors": [ {
                 |    "typ": "ExpressionParserCompilationError",
                 |    "message": "Failed to parse expression: Bad expression type, expected: Boolean, found: Long(5)",
                 |    "description": "There is problem with expression in field Some(condition) - it could not be parsed.",
                 |    "fieldName": "condition",
                 |    "errorType": "SaveAllowed"
                 |  } ],
                 |  "validationPerformed": true
                 |}""".stripMargin))
        }
      }
      "not authenticated should" - {
        "forbid access" in {
          given()
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
                 |""".stripMargin
            )
            .and
            .auth()
            .none()
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
            .and()
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
            .and()
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
            .and()
            .auth()
            .none()
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

}
