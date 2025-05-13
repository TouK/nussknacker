package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.given
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers.equalTo
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.test.{NuRestAssureMatchers, PatientScalaFutures, RestAssuredVerboseLoggingIfValidationFails}
import pl.touk.nussknacker.test.base.it.{NuItTest, WithAccessControlCheckingConfigScenarioHelper}
import pl.touk.nussknacker.test.config.{
  WithAccessControlCheckingConfigRestAssuredUsersExtensions,
  WithAccessControlCheckingDesignerConfig,
  WithMockableDeploymentManager
}
import pl.touk.nussknacker.test.config.WithAccessControlCheckingDesignerConfig.TestCategory.{Category1, Category2}
import pl.touk.nussknacker.test.config.WithAccessControlCheckingDesignerConfig.TestProcessingType.{
  Streaming1,
  Streaming2
}

class NodesApiHttpServiceSecuritySpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithAccessControlCheckingDesignerConfig
    with WithAccessControlCheckingConfigScenarioHelper
    with WithMockableDeploymentManager
    with WithAccessControlCheckingConfigRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails
    with PatientScalaFutures {

  "The endpoint for nodes additional info when" - {
    "authenticated should" - {
      "return additional info for node with expression in the allowed category scenario" in {
        val allowedCategoryScenarioName = "s1"
        given()
          .applicationState {
            createSavedScenario(exampleScenario(allowedCategoryScenarioName), category = Category1)
            createSavedScenario(exampleScenario("s2"), category = Category2)
          }
          .when()
          .basicAuthLimitedReader()
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
          .post(s"$nuDesignerHttpAddress/api/nodes/$allowedCategoryScenarioName/additionalInfo")
          .Then()
          .statusCode(200)
          .equalsJsonBody(
            s"""{
               |  "content": "\\nSamples:\\n\\n| id  | value |\\n| --- | ----- |\\n| a   | generated |\\n| b   | not existent |\\n\\nResults for a can be found [here](http://touk.pl?id=a)\\n",
               |  "type": "MarkdownAdditionalInfo"
               |}""".stripMargin
          )
      }
      "return additional info for node with expression in scenario from the forbidden category" in {
        val forbiddenCategoryScenarioName = "s2"
        given()
          .applicationState {
            createSavedScenario(exampleScenario("s1"), category = Category1)
            createSavedScenario(exampleScenario(forbiddenCategoryScenarioName), category = Category2)
          }
          .when()
          .basicAuthLimitedReader()
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
          .post(s"$nuDesignerHttpAddress/api/nodes/$forbiddenCategoryScenarioName/additionalInfo")
          .Then()
          .statusCode(404)
          .equalsPlainBody("No scenario s2 found")
      }
    }
    "not authenticated should" - {
      "forbid access" in {
        given()
          .applicationState {
            createSavedScenario(exampleScenario("s1"), category = Category1)
            createSavedScenario(exampleScenario("s2"), category = Category2)
          }
          .when()
          .basicAuthUnknownUser()
          .jsonBody(exampleNodesAdditionalInfoRequestBody)
          .post(s"$nuDesignerHttpAddress/api/nodes/s1/additionalInfo")
          .Then()
          .statusCode(401)
          .body(equalTo("The supplied authentication is invalid"))
      }
    }
    "no credentials were passed should" - {
      "authenticate as anonymous and return additional info for node related to anonymous role category" in {
        val allowedCategoryScenarioName = "s2"
        given()
          .applicationState {
            createSavedScenario(exampleScenario("s1"), category = Category1)
            createSavedScenario(exampleScenario(allowedCategoryScenarioName), category = Category2)
          }
          .when()
          .noAuth()
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
          .post(s"$nuDesignerHttpAddress/api/nodes/$allowedCategoryScenarioName/additionalInfo")
          .Then()
          .statusCode(200)
          .equalsJsonBody(
            s"""{
               |  "content": "\\nSamples:\\n\\n| id  | value |\\n| --- | ----- |\\n| a   | generated |\\n| b   | not existent |\\n\\nResults for a can be found [here](http://touk.pl?id=a)\\n",
               |  "type": "MarkdownAdditionalInfo"
               |}""".stripMargin
          )
      }
    }
  }

  "The endpoint for nodes validation when" - {
    "authenticated should" - {
      "validate node of scenario in the allowed category" in {
        val allowedCategoryScenarioName = "s1"
        given()
          .applicationState {
            createSavedScenario(exampleScenario(allowedCategoryScenarioName), category = Category1)
            createSavedScenario(exampleScenario("s2"), category = Category2)
          }
          .when()
          .basicAuthLimitedReader()
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
          .post(s"$nuDesignerHttpAddress/api/nodes/$allowedCategoryScenarioName/validation")
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
      "validate node of scenario in the forbidden category" in {
        val forbiddenCategoryScenarioName = "s2"
        given()
          .applicationState {
            createSavedScenario(exampleScenario("s1"), category = Category1)
            createSavedScenario(exampleScenario(forbiddenCategoryScenarioName), category = Category2)
          }
          .when()
          .basicAuthLimitedReader()
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
          .post(s"$nuDesignerHttpAddress/api/nodes/$forbiddenCategoryScenarioName/validation")
          .Then()
          .statusCode(404)
          .equalsPlainBody("No scenario s2 found")
      }
    }
    "not authenticated should" - {
      "forbid access" in {
        given()
          .applicationState {
            createSavedScenario(exampleScenario("s1"), category = Category1)
            createSavedScenario(exampleScenario("s2"), category = Category2)
          }
          .when()
          .basicAuthUnknownUser()
          .jsonBody(exampleNodesValidationRequestBody)
          .post(s"$nuDesignerHttpAddress/api/nodes/s1/validation")
          .Then()
          .statusCode(401)
          .body(equalTo("The supplied authentication is invalid"))
      }
    }
    "no credentials were passed should" - {
      "authenticate as anonymous and validate node related to anonymous role category" in {
        val allowedCategoryScenarioName = "s2"
        given()
          .applicationState {
            createSavedScenario(exampleScenario("s1"), category = Category1)
            createSavedScenario(exampleScenario(allowedCategoryScenarioName), category = Category2)
          }
          .when()
          .noAuth()
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
          .post(s"$nuDesignerHttpAddress/api/nodes/$allowedCategoryScenarioName/validation")
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
    }
  }

  "The endpoint for properties additional info when" - {
    "authenticated should" - {
      "return additional info of scenario in the allowed category" in {
        val allowedCategoryScenarioName = "s1"
        given()
          .applicationState {
            createSavedScenario(exampleScenario(allowedCategoryScenarioName), category = Category1)
            createSavedScenario(exampleScenario("s2"), category = Category2)
          }
          .when()
          .basicAuthLimitedReader()
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
          .post(s"$nuDesignerHttpAddress/api/properties/$allowedCategoryScenarioName/additionalInfo")
          .Then()
          .statusCode(200)
          .equalsJsonBody(
            s"""{
               |  "content": "2 threads will be used on environment 'test'",
               |  "type": "MarkdownAdditionalInfo"
               |}""".stripMargin
          )
      }
      "return additional info of scenario in the forbidden category" in {
        val forbiddenCategoryScenarioName = "s2"
        given()
          .applicationState {
            createSavedScenario(exampleScenario("s1"), category = Category1)
            createSavedScenario(exampleScenario(forbiddenCategoryScenarioName), category = Category2)
          }
          .when()
          .basicAuthLimitedReader()
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
          .post(s"$nuDesignerHttpAddress/api/properties/$forbiddenCategoryScenarioName/additionalInfo")
          .Then()
          .statusCode(404)
          .equalsPlainBody("No scenario s2 found")
      }
    }
    "not authenticated should" - {
      "forbid access" in {
        given()
          .applicationState {
            createSavedScenario(exampleScenario("s1"), category = Category1)
            createSavedScenario(exampleScenario("s2"), category = Category2)
          }
          .when()
          .jsonBody(examplePropertiesAdditionalInfoRequestBody)
          .basicAuthUnknownUser()
          .when()
          .post(s"$nuDesignerHttpAddress/api/properties/s1/additionalInfo")
          .Then()
          .statusCode(401)
          .body(equalTo("The supplied authentication is invalid"))
      }
    }
    "no credentials were passed should" - {
      "authenticate as anonymous and return additional info of scenario related to anonymous role category" in {
        val allowedCategoryScenarioName = "s2"
        given()
          .applicationState {
            createSavedScenario(exampleScenario("s1"), category = Category1)
            createSavedScenario(exampleScenario(allowedCategoryScenarioName), category = Category2)
          }
          .when()
          .noAuth()
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
          .post(s"$nuDesignerHttpAddress/api/properties/$allowedCategoryScenarioName/additionalInfo")
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
  }

  "The endpoint for properties validation when" - {
    "authenticated should" - {
      "validate properties of scenario in the allowed category" in {
        val allowedCategoryScenarioName = "s1"
        given()
          .applicationState {
            createSavedScenario(exampleScenario(allowedCategoryScenarioName), category = Category1)
            createSavedScenario(exampleScenario("s2"), category = Category2)
          }
          .when()
          .basicAuthLimitedReader()
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
          .post(s"$nuDesignerHttpAddress/api/properties/$allowedCategoryScenarioName/validation")
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
               |      "details": null
               |    }
               |  ],
               |  "validationPerformed": true
               |}""".stripMargin)
      }
      "validate properties of scenario in the forbidden category" in {
        val forbiddenCategoryScenarioName = "s2"
        given()
          .applicationState {
            createSavedScenario(exampleScenario("s1"), category = Category1)
            createSavedScenario(exampleScenario(forbiddenCategoryScenarioName), category = Category2)
          }
          .when()
          .basicAuthLimitedReader()
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
          .post(s"$nuDesignerHttpAddress/api/properties/$forbiddenCategoryScenarioName/validation")
          .Then()
          .statusCode(404)
          .equalsPlainBody("No scenario s2 found")
      }
    }
    "not authenticated should" - {
      "forbid access" in {
        given
          .applicationState {
            createSavedScenario(exampleScenario("s1"), category = Category1)
            createSavedScenario(exampleScenario("s2"), category = Category2)
          }
          .when()
          .jsonBody(examplePropertiesValidationRequestBody)
          .basicAuthUnknownUser()
          .post(s"$nuDesignerHttpAddress/api/properties/s1/validation")
          .Then()
          .statusCode(401)
          .body(equalTo("The supplied authentication is invalid"))
      }
    }
    "no credentials were passed should" - {
      "authenticate as anonymous and validate properties of scenario related to anonymous role category" in {
        val allowedCategoryScenarioName = "s2"
        given()
          .applicationState {
            createSavedScenario(exampleScenario("s1"), category = Category1)
            createSavedScenario(exampleScenario(allowedCategoryScenarioName), category = Category2)
          }
          .when()
          .noAuth()
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
          .post(s"$nuDesignerHttpAddress/api/properties/$allowedCategoryScenarioName/validation")
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
               |      "details": null
               |    }
               |  ],
               |  "validationPerformed": true
               |}""".stripMargin)
      }
    }
  }

  "The endpoint for parameters validation when" - {
    "authenticated should" - {
      "validate parameter of scenario related the allowed processing type" in {
        given()
          .when()
          .basicAuthLimitedReader()
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
          .post(s"$nuDesignerHttpAddress/api/parameters/${Streaming1.stringify}/validate")
          .Then()
          .statusCode(200)
          .equalsJsonBody(
            s"""{
               |  "validationErrors": [],
               |  "validationPerformed": true
               |}""".stripMargin
          )
      }
      "validate parameter of scenario related the forbidden processing type" in {
        given()
          .when()
          .basicAuthLimitedReader()
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
          .post(s"$nuDesignerHttpAddress/api/parameters/${Streaming2.stringify}/validate")
          .Then()
          .statusCode(403)
          .equalsPlainBody("The supplied authentication is not authorized to access this resource")
      }
    }
    "not authenticated should" - {
      "forbid access" in {
        given()
          .when()
          .basicAuthUnknownUser()
          .jsonBody(exampleParametersValidationRequestBody)
          .post(s"$nuDesignerHttpAddress/api/parameters/${Streaming1.stringify}/validate")
          .Then()
          .statusCode(401)
          .body(equalTo("The supplied authentication is invalid"))
      }
    }
    "no credentials were passed should" - {
      "authenticate as anonymous and validate parameter of scenario related to anonymous role category's processing type" in {
        given()
          .when()
          .noAuth()
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
          .post(s"$nuDesignerHttpAddress/api/parameters/${Streaming2.stringify}/validate")
          .Then()
          .statusCode(200)
          .equalsJsonBody(
            s"""{
               |  "validationErrors": [],
               |  "validationPerformed": true
               |}""".stripMargin
          )
      }
    }
  }

  "The endpoint for parameters suggestions when" - {
    "authenticated should" - {
      "do their job when the processing type is allowed" in {
        given()
          .when()
          .basicAuthLimitedReader()
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
          .when()
          .post(s"$nuDesignerHttpAddress/api/parameters/${Streaming1.stringify}/suggestions")
          .Then()
          .statusCode(200)
          .body("methodName[0]", equalTo("#input"))
      }
      "be forbidden when the processing type is disallowed" in {
        given()
          .when()
          .basicAuthLimitedReader()
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
          .when()
          .post(s"$nuDesignerHttpAddress/api/parameters/${Streaming2.stringify}/suggestions")
          .Then()
          .statusCode(403)
          .equalsPlainBody("The supplied authentication is not authorized to access this resource")
      }
    }
    "not authenticated should" - {
      "forbid access" in {
        given()
          .when()
          .jsonBody(exampleParametersSuggestionsRequestBody)
          .basicAuthUnknownUser()
          .post(s"$nuDesignerHttpAddress/api/parameters/${Streaming1.stringify}/suggestions")
          .Then()
          .statusCode(401)
          .body(equalTo("The supplied authentication is invalid"))
      }
    }
    "no credentials were passed should" - {
      "authenticate as anonymous and do the suggestion in case of scenario related to anonymous role category's processing type" in {
        given()
          .when()
          .noAuth()
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
          .when()
          .post(s"$nuDesignerHttpAddress/api/parameters/${Streaming2.stringify}/suggestions")
          .Then()
          .statusCode(200)
          .body("methodName[0]", equalTo("#input"))
      }
    }
  }

  private def exampleScenario(name: String) = ScenarioBuilder
    .streaming(name)
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

  private lazy val exampleNodesValidationRequestBody =
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

  private lazy val examplePropertiesAdditionalInfoRequestBody =
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

  private lazy val examplePropertiesValidationRequestBody =
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
       |  "id": "test"
       |}""".stripMargin

  private lazy val exampleParametersValidationRequestBody =
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
       |""".stripMargin

  private lazy val exampleParametersSuggestionsRequestBody =
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

}
