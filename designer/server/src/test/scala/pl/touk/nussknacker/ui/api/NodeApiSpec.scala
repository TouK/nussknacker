package pl.touk.nussknacker.ui.api

import io.circe.Encoder
import io.restassured.RestAssured.given
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import io.restassured.specification.RequestSpecification
import org.hamcrest.Matchers.equalTo
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.ExpressionParserCompilationError
import pl.touk.nussknacker.engine.api.displayedgraph.ProcessProperties
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.typed.{TypedMap, typing}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{ProcessAdditionalFields, StreamMetaData}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.spel
import pl.touk.nussknacker.engine.graph.expression.NodeExpressionId.DefaultExpressionId
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.{Enricher, NodeData}
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.kafka.KafkaFactory.{SinkValueParamName, TopicParamName}
import pl.touk.nussknacker.restmodel.definition.UIValueParameter
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.test.{
  NuRestAssureExtensions,
  NuRestAssureMatchers,
  PatientScalaFutures,
  RestAssuredVerboseLogging
}
import pl.touk.nussknacker.ui.api.helpers._
import pl.touk.nussknacker.ui.suggester.CaretPosition2d

class NodeApiSpec
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
          val data: NodeData =
            Enricher("enricher", ServiceRef("paramService", List(Parameter("id", Expression.spel("'a'")))), "out", None)

          sendNodeAsJsonAsAllpermuser(data)
            .when()
            .post(s"$nuDesignerHttpAddress/api/nodes/${process.id}/additionalInfo")
            .Then()
            .statusCode(200)
            .body(
              equalsJson(s"""{
                   |  "content": "\\nSamples:\\n\\n| id  | value |\\n| --- | ----- |\\n| a   | generated |\\n| b   | not existent |\\n\\nResults for a can be found [here](http://touk.pl?id=a)\\n",
                   |  "type": "MarkdownAdditionalInfo"
                   |}""".stripMargin)
            )

          val dataEmpty: NodeData = Enricher("1", ServiceRef("otherService", List()), "out", None)

          sendNodeAsJsonAsAllpermuser(dataEmpty)
            .when()
            .post(s"$nuDesignerHttpAddress/api/nodes/${process.id}/additionalInfo")
            .Then()
            .statusCode(200)
            .body(
              equalTo("")
            )
        }
        "return 404 for not existent process" in {
          val data: NodeData =
            Enricher("enricher", ServiceRef("paramService", List(Parameter("id", Expression.spel("'a'")))), "out", None)

          val wrongName: String = "wrongProcessName"

          sendNodeAsJsonAsAllpermuser(data)
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
          val data: NodeData =
            Enricher("enricher", ServiceRef("paramService", List(Parameter("id", Expression.spel("'a'")))), "out", None)
          val json = Encoder[NodeData].apply(data)

          given
            .contentType("application/json")
            .body(json.toString())
            .and()
            .auth()
            .none
            .when()
            .post(s"$nuDesignerHttpAddress/api/nodes/${process.id}/additionalInfo")
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
          val data: node.Filter = node.Filter("id", Expression.spel("#longValue > 1"))
          val request = NodeValidationRequest(
            data,
            ProcessProperties(StreamMetaData()),
            Map("existButString" -> Typed[String], "longValue" -> Typed[Long]),
            None,
            None
          )

          sendRequestAsJsonAsAllpermuser(request)
            .when()
            .post(s"$nuDesignerHttpAddress/api/nodes/${process.id}/validation")
            .Then()
            .statusCode(200)
            .body("validationErrors[0]", equalTo(null))
        }
        "validate filter node when wrong parameter type is given" in {
          createSavedProcess(process, TestCategories.Category1, TestProcessingTypes.Streaming)
          val data: node.Filter = node.Filter("id", Expression.spel("#existButString"))
          val request = NodeValidationRequest(
            data,
            ProcessProperties(StreamMetaData()),
            Map("existButString" -> Typed[String], "longValue" -> Typed[Long]),
            None,
            None
          )

          sendRequestAsJsonAsAllpermuser(request)
            .when()
            .post(s"$nuDesignerHttpAddress/api/nodes/${process.id}/validation")
            .Then()
            .statusCode(200)
            .body(
              equalsJson(
                Encoder[NodeValidationResult]
                  .apply(
                    NodeValidationResult(
                      parameters = None,
                      expressionType = Some(typing.Unknown),
                      validationErrors = List(
                        PrettyValidationErrors.formatErrorMessage(
                          ExpressionParserCompilationError(
                            "Bad expression type, expected: Boolean, found: String",
                            data.id,
                            Some(DefaultExpressionId),
                            data.expression.expression
                          )
                        )
                      ),
                      validationPerformed = true
                    )
                  )
                  .toString()
              )
            )
        }
        "validate incorrect sink expression" in {
          createSavedProcess(process, TestCategories.Category1, TestProcessingTypes.Streaming)

          val data: node.Sink = node.Sink(
            "mysink",
            SinkRef(
              "kafka-string",
              List(
                Parameter(SinkValueParamName, Expression.spel("notvalidspelexpression")),
                Parameter(TopicParamName, Expression.spel("'test-topic'"))
              )
            ),
            None,
            None
          )
          val request = NodeValidationRequest(
            data,
            ProcessProperties(StreamMetaData()),
            Map("existButString" -> Typed[String], "longValue" -> Typed[Long]),
            None,
            None
          )

          sendRequestAsJsonAsAllpermuser(request)
            .when()
            .post(s"$nuDesignerHttpAddress/api/nodes/${process.id}/validation")
            .Then()
            .statusCode(200)
            .body("validationErrors[0].typ", equalTo("ExpressionParserCompilationError"))
            .body(
              "validationErrors[0].message",
              equalTo(
                "Failed to parse expression: Non reference 'notvalidspelexpression' occurred. Maybe you missed '#' in front of it?"
              )
            )
            .body("validationErrors[0].fieldName", equalTo(SinkValueParamName))
        }
        "validate node using dictionaries" in {
          createSavedProcess(process, TestCategories.Category1, TestProcessingTypes.Streaming)
          val data: node.Filter = node.Filter("id", Expression.spel("#DICT.Bar != #DICT.Foo"))
          val request           = NodeValidationRequest(data, ProcessProperties(StreamMetaData()), Map(), None, None)

          sendRequestAsJsonAsAllpermuser(request)
            .when()
            .post(s"$nuDesignerHttpAddress/api/nodes/${process.id}/validation")
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
          val blankValue        = " "
          val data: node.Filter = node.Filter(blankValue, Expression.spel("true"))
          val request           = NodeValidationRequest(data, ProcessProperties(StreamMetaData()), Map(), None, None)

          sendRequestAsJsonAsAllpermuser(request)
            .when()
            .post(s"$nuDesignerHttpAddress/api/nodes/${process.id}/validation")
            .Then()
            .statusCode(200)
            .body("validationErrors[0].typ", equalTo("NodeIdValidationError"))
            .body("validationErrors[0].message", equalTo("Node name cannot be blank"))
            .body("validationErrors[0].fieldName", equalTo("$id"))
        }
      }
      "not authenticated should" - {
        "forbid access" in {
          createSavedProcess(process, TestCategories.Category1, TestProcessingTypes.Streaming)
          val data: node.Filter = node.Filter("id", Expression.spel("#existButString"))
          val request = NodeValidationRequest(
            data,
            ProcessProperties(StreamMetaData()),
            Map("existButString" -> Typed[String], "longValue" -> Typed[Long]),
            None,
            None
          )
          val json = Encoder[NodeValidationRequest].apply(request)

          given()
            .auth()
            .none()
            .and()
            .contentType("application/json")
            .body(json.toString())
            .when()
            .post(s"$nuDesignerHttpAddress/api/nodes/${process.id}/validation")
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
          val processProperties = ProcessProperties.combineTypeSpecificProperties(
            StreamMetaData(),
            additionalFields = ProcessAdditionalFields(
              None,
              Map("numberOfThreads" -> "2", "environment" -> "test"),
              StreamMetaData.typeName
            )
          )
          val json = Encoder[ProcessProperties].apply(processProperties)

          given()
            .contentType("application/json")
            .body(json.toString())
            .and()
            .auth()
            .basic("admin", "admin")
            .when()
            .post(s"$nuDesignerHttpAddress/api/properties/${process.id}/additionalInfo")
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
          val processProperties = ProcessProperties.combineTypeSpecificProperties(
            StreamMetaData(),
            additionalFields = ProcessAdditionalFields(
              None,
              Map("numberOfThreads" -> "2", "environment" -> "test"),
              StreamMetaData.typeName
            )
          )
          val json = Encoder[ProcessProperties].apply(processProperties)

          given()
            .contentType("application/json")
            .body(json.toString())
            .and()
            .auth()
            .none()
            .when()
            .post(s"$nuDesignerHttpAddress/api/properties/${process.id}/additionalInfo")
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
          val request = PropertiesValidationRequest(
            additionalFields = ProcessAdditionalFields(
              properties = StreamMetaData().toMap ++ Map("numberOfThreads" -> "a", "environment" -> "test"),
              metaDataType = StreamMetaData.typeName,
              description = None
            ),
            id = process.id
          )
          val json = Encoder[PropertiesValidationRequest].apply(request)

          given
            .contentType("application/json")
            .body(json.toString())
            .and()
            .auth()
            .basic("allpermuser", "allpermuser")
            .post(s"$nuDesignerHttpAddress/api/properties/${process.id}/validation")
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
          val blankValue = " "
          val request = PropertiesValidationRequest(
            additionalFields = ProcessAdditionalFields(
              properties = StreamMetaData().toMap ++ Map("numberOfThreads" -> "a", "environment" -> "test"),
              metaDataType = StreamMetaData.typeName,
              description = None
            ),
            id = blankValue
          )
          val json = Encoder[PropertiesValidationRequest].apply(request)

          given
            .contentType("application/json")
            .body(json.toString())
            .and()
            .auth()
            .basic("allpermuser", "allpermuser")
            .post(s"$nuDesignerHttpAddress/api/properties/${process.id}/validation")
            .Then()
            .statusCode(200)
            .body(
              equalsJson(s"""{
                   |    "parameters": null,
                   |    "expressionType": null,
                   |    "validationErrors": [
                   |        {
                   |            "typ": "ScenarioIdError",
                   |            "message": "Scenario name cannot be blank",
                   |            "description": "Blank scenario name",
                   |            "fieldName": "$$id",
                   |            "errorType": "SaveAllowed"
                   |        },
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
          val request = PropertiesValidationRequest(
            additionalFields = ProcessAdditionalFields(
              properties = StreamMetaData().toMap ++ Map("numberOfThreads" -> "a", "environment" -> "test"),
              metaDataType = StreamMetaData.typeName,
              description = None
            ),
            id = process.id
          )
          val json = Encoder[PropertiesValidationRequest].apply(request)

          given
            .contentType("application/json")
            .body(json.toString())
            .and()
            .auth()
            .none()
            .post(s"$nuDesignerHttpAddress/api/properties/${process.id}/validation")
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
          val request = ParametersValidationRequest(
            parameters = List(UIValueParameter("condition", Typed[Boolean], spel("#input.amount > 2"))),
            variableTypes = Map("input" -> Typed.fromInstance(TypedMap(Map("amount" -> 5L))))
          )
          val json = Encoder[ParametersValidationRequest].apply(request)

          given()
            .contentType("application/json")
            .body(json.toString())
            .and
            .auth()
            .basic("allpermuser", "allpermuser")
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
          val request = ParametersValidationRequest(
            parameters = List(UIValueParameter("condition", Typed[Boolean], spel("#input.amount"))),
            variableTypes = Map("input" -> Typed.fromInstance(TypedMap(Map("amount" -> 5L))))
          )
          val json = Encoder[ParametersValidationRequest].apply(request)

          given()
            .contentType("application/json")
            .body(json.toString())
            .and
            .auth()
            .basic("allpermuser", "allpermuser")
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
          val request = ParametersValidationRequest(
            parameters = List(UIValueParameter("condition", Typed[Boolean], spel("#input.amount > 2"))),
            variableTypes = Map("input" -> Typed.fromInstance(TypedMap(Map("amount" -> 5L))))
          )
          val json = Encoder[ParametersValidationRequest].apply(request)

          given()
            .contentType("application/json")
            .body(json.toString())
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
          val request = ExpressionSuggestionRequest(
            caretPosition2d = CaretPosition2d(0, 5),
            expression = spel("#inpu"),
            variableTypes = Map("input" -> Typed.fromInstance(TypedMap(Map("amount" -> 5L))))
          )
          val json = Encoder[ExpressionSuggestionRequest].apply(request)

          given()
            .auth()
            .basic("allpermuser", "allpermuser")
            .and()
            .contentType("application/json")
            .body(json.toString())
            .when()
            .post(s"$nuDesignerHttpAddress/api/parameters/streaming/suggestions")
            .Then()
            .statusCode(200)
            .body("methodName[0]", equalTo("#input"))
        }
        "not suggest anything if no such parameters exist" in {
          val request = ExpressionSuggestionRequest(
            caretPosition2d = CaretPosition2d(0, 5),
            expression = spel("#inpu"),
            variableTypes = Map()
          )
          val json = Encoder[ExpressionSuggestionRequest].apply(request)

          given()
            .auth()
            .basic("allpermuser", "allpermuser")
            .and()
            .contentType("application/json")
            .body(json.toString())
            .when()
            .post(s"$nuDesignerHttpAddress/api/parameters/streaming/suggestions")
            .Then()
            .statusCode(200)
            .body(equalTo("[]"))
        }
      }
      "not authenticated should" - {
        "forbid access" in {
          val request = ExpressionSuggestionRequest(
            caretPosition2d = CaretPosition2d(0, 5),
            expression = spel("#inpu"),
            variableTypes = Map("input" -> Typed.fromInstance(TypedMap(Map("amount" -> 5L))))
          )
          val json = Encoder[ExpressionSuggestionRequest].apply(request)

          given()
            .auth()
            .none()
            .and()
            .contentType("application/json")
            .body(json.toString())
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

  def sendNodeAsJsonAsAdmin(data: NodeData): RequestSpecification =
    sendNodeAsJson(data, "admin")

  def sendNodeAsJsonAsAllpermuser(data: NodeData): RequestSpecification =
    sendNodeAsJson(data, "allpermuser")

  def sendNodeAsJson(node: NodeData, user: String): RequestSpecification = {
    val json = Encoder[NodeData].apply(node)

    given
      .contentType("application/json")
      .body(json.toString())
      .and()
      .auth()
      .basic(user, user)
  }

  def sendRequestAsJsonAsAdmin(request: NodeValidationRequest): RequestSpecification =
    sendRequestToValidationAsJson(request, "admin")

  def sendRequestAsJsonAsAllpermuser(request: NodeValidationRequest): RequestSpecification =
    sendRequestToValidationAsJson(request, "allpermuser")

  def sendRequestToValidationAsJson(request: NodeValidationRequest, user: String): RequestSpecification = {
    val json = Encoder[NodeValidationRequest].apply(request)

    given
      .contentType("application/json")
      .body(json.toString())
      .and()
      .auth()
      .basic(user, user)
  }

}
