package pl.touk.nussknacker.ui.api.testing

import com.dimafeng.testcontainers._
import com.typesafe.config.{Config, ConfigValueFactory}
import com.typesafe.config.ConfigValueFactory.fromMap
import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, Json, JsonObject}
import io.circe.parser._
import io.circe.syntax.EncoderOps
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.apache.kafka.clients.admin.NewTopic
import org.hamcrest.Matchers.equalTo
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.development.manager.MockableDeploymentManagerProvider.MockableDeploymentManager
import pl.touk.nussknacker.engine.api.json.decoders.TypingResultDecoder
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.test.docker.WithKafkaContainer
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.kafka.{KafkaComponentsConfig, KafkaUtils}
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.ContentTypes
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.test.{
  NuRestAssureExtensions,
  PatientScalaFutures,
  RestAssuredVerboseLoggingIfValidationFails
}
import pl.touk.nussknacker.test.ProcessUtils.convertToAnyShouldWrapper
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig
import pl.touk.nussknacker.test.containers.WithDockerContainers
import pl.touk.nussknacker.test.processes.WithScenarioActivitySpecAsserts.UsersBasicAuth
import pl.touk.nussknacker.ui.api.ScenarioValidationRequest
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.TestSourceParameters
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.ScenarioTestData
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Validate.ScenarioTestValidationRequest
import pl.touk.nussknacker.ui.process.test.testdataformat.CommonDataFormatHandler.InputVariablesParameterName

import java.util.{Collections, UUID}
import scala.jdk.CollectionConverters._

class SchemalessKafkaJsonTypeTests
    extends AnyFreeSpecLike
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithSimplifiedConfigScenarioHelper
    with RestAssuredVerboseLoggingIfValidationFails
    with PatientScalaFutures
    with WithAdHocTestsLogic
    with WithAdHocInvalidParametersTestsLogic
    with WithDockerContainers
    with WithKafkaContainer
    with ForAllTestContainer
    with StrictLogging
    with NuRestAssureExtensions {

  private lazy val defaultKafkaConfig: KafkaComponentsConfig = KafkaComponentsConfig(
    kafkaProperties = Map("bootstrap.servers" -> hostKafkaAddress),
    kafkaEspProperties = None,
  )

  override val container: Container = kafkaContainer

  private val validJson = """|{
                             |  "input": {
                             |    "name": "FooBar"
                             |  },
                             |  "inputMeta": {
                             |    "key" : "",
                             |    "topic" : "",
                             |    "partition" : 0,
                             |    "offset" : 0,
                             |    "timestamp" : 0,
                             |    "timestampType" : "NO_TIMESTAMP_TYPE",
                             |    "headers" : {
                             |      "field" : ""
                             |    },
                             |    "leaderEpoch" : 0
                             |  }
                             |}""".stripMargin

  private val invalidJson = """|{
                               |  "products": [
                               |    {"id": 1, "name": "Laptop", "price": 120a0.00},
                               |    {"id": 2, "name": "Smartphone", "price": 800.50},
                               |    {"id": 3, "name": "Tablet", "price": 450.75}
                               |  ]
                               |}""".stripMargin

  private val sourceTopicName = "someInputTopic"

  private val sinkTopicName = "someOutputTopic"

  private val variablesNodeName = "vars"
  private val nameVariable      = "name"
  private val ageVariable       = "age"
  private val isAdultVariable   = "isAdult"

  override protected val exampleScenarioSourceId: String = "start"

  override protected val exampleScenario: CanonicalProcess = {
    ScenarioBuilder
      .streaming("without-schema")
      .parallelism(1)
      .source(
        exampleScenarioSourceId,
        "kafka",
        "Topic"        -> s"'$sourceTopicName'".spel,
        "Content type" -> "'JSON'".spel,
        "Data sample"  -> Expression.json("{\"name\": \"Tom\"}")
      )
      // We add filtering logic to ensure that types are correctly verified during testing
      .filter("filter", "#input.name != 'asdf'".spel)
      .emptySink(
        "end",
        "kafka",
        "Key"                   -> "".spel,
        "Raw editor"            -> "true".spel,
        "Value"                 -> "#input".spel,
        "Topic"                 -> s"'$sinkTopicName'".spel,
        "Content type"          -> "'JSON'".spel,
        "Value validation mode" -> s"'${ValidationMode.lax.name}'".spel
      )
  }

  override protected val validParameters: TestSourceParameters =
    TestSourceParameters(exampleScenarioSourceId, Map(InputVariablesParameterName -> Expression.json(validJson)))

  override protected val invalidParameters: TestSourceParameters =
    TestSourceParameters(exampleScenarioSourceId, Map(InputVariablesParameterName -> Expression.json(invalidJson)))

  override protected val expectedValidationErrorsOnInvalidParametersJson: String =
    s"""
       |[
       |  {
       |    "typ": "ExpressionParserCompilationError",
       |    "message": "expected } or , got 'a0.00}...'",
       |    "description": "There is problem with expression in field [$InputVariablesParameterName] - it could not be parsed.",
       |    "fieldName": "$InputVariablesParameterName",
       |    "errorType": "SaveAllowed",
       |    "details": {"start":{"column":44,"row":2},"end":{"column":45,"row":2},"type":"CoordinatesBasedTextRange"}
       |  }
       |]""".stripMargin

  override protected def expectedTestParametersJson: String = {
    s"""[
       |  {
       |    "sourceId": "start",
       |    "sourceName": "start",
       |    "parameters": [
       |      {
       |        "name": "$InputVariablesParameterName",
       |        "typ": {
       |          "display": "Record{input: Record{name: String}, inputMeta: InputMeta[String]}",
       |          "type": "TypedObjectTypingResult",
       |          "fields": {
       |            "input": {
       |              "display": "Record{name: String}",
       |              "type": "TypedObjectTypingResult",
       |              "fields": {
       |                "name": {
       |                  "display": "String",
       |                  "type": "TypedClass",
       |                  "refClazzName": "java.lang.String",
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
       |                  "display": "String",
       |                  "type": "TypedClass",
       |                  "refClazzName": "java.lang.String",
       |                  "params": []
       |                }
       |              ]
       |            },
       |            "inputMeta": {
       |              "display": "InputMeta[String]",
       |              "type": "TypedObjectTypingResult",
       |              "fields": {
       |                "timestamp": {
       |                  "display": "Long",
       |                  "type": "TypedClass",
       |                  "refClazzName": "java.lang.Long",
       |                  "params": []
       |                },
       |                "partition": {
       |                  "display": "Integer",
       |                  "type": "TypedClass",
       |                  "refClazzName": "java.lang.Integer",
       |                  "params": []
       |                },
       |                "timestampType": {
       |                  "display": "TimestampType",
       |                  "type": "TypedClass",
       |                  "refClazzName": "org.apache.kafka.common.record.TimestampType",
       |                  "params": []
       |                },
       |                "key": {
       |                  "display": "String",
       |                  "type": "TypedClass",
       |                  "refClazzName": "java.lang.String",
       |                  "params": []
       |                },
       |                "offset": {
       |                  "display": "Long",
       |                  "type": "TypedClass",
       |                  "refClazzName": "java.lang.Long",
       |                  "params": []
       |                },
       |                "leaderEpoch": {
       |                  "display": "Integer",
       |                  "type": "TypedClass",
       |                  "refClazzName": "java.lang.Integer",
       |                  "params": []
       |                },
       |                "topic": {
       |                  "display": "String",
       |                  "type": "TypedClass",
       |                  "refClazzName": "java.lang.String",
       |                  "params": []
       |                },
       |                "headers": {
       |                  "display": "Map[String,String]",
       |                  "type": "TypedClass",
       |                  "refClazzName": "java.util.Map",
       |                  "params": [
       |                    {
       |                      "display": "String",
       |                      "type": "TypedClass",
       |                      "refClazzName": "java.lang.String",
       |                      "params": []
       |                    },
       |                    {
       |                      "display": "String",
       |                      "type": "TypedClass",
       |                      "refClazzName": "java.lang.String",
       |                      "params": []
       |                    }
       |                  ]
       |                }
       |              },
       |              "refClazzName": "java.util.Map",
       |              "params": [
       |                {
       |                  "display": "Unknown",
       |                  "type": "Unknown",
       |                  "refClazzName": "java.lang.Object",
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
       |              "display": "Record{headers: Map[String,String], key: String, leaderEpoch: Integer, name: String, offset: Long, partition: Integer, timestamp: Long, timestampType: TimestampType, topic: String}",
       |              "type": "TypedObjectTypingResult",
       |              "fields": {
       |                "name": {
       |                  "display": "String",
       |                  "type": "TypedClass",
       |                  "refClazzName": "java.lang.String",
       |                  "params": []
       |                },
       |                "timestamp": {
       |                  "display": "Long",
       |                  "type": "TypedClass",
       |                  "refClazzName": "java.lang.Long",
       |                  "params": []
       |                },
       |                "partition": {
       |                  "display": "Integer",
       |                  "type": "TypedClass",
       |                  "refClazzName": "java.lang.Integer",
       |                  "params": []
       |                },
       |                "timestampType": {
       |                  "display": "TimestampType",
       |                  "type": "TypedClass",
       |                  "refClazzName": "org.apache.kafka.common.record.TimestampType",
       |                  "params": []
       |                },
       |                "key": {
       |                  "display": "String",
       |                  "type": "TypedClass",
       |                  "refClazzName": "java.lang.String",
       |                  "params": []
       |                },
       |                "offset": {
       |                  "display": "Long",
       |                  "type": "TypedClass",
       |                  "refClazzName": "java.lang.Long",
       |                  "params": []
       |                },
       |                "leaderEpoch": {
       |                  "display": "Integer",
       |                  "type": "TypedClass",
       |                  "refClazzName": "java.lang.Integer",
       |                  "params": []
       |                },
       |                "topic": {
       |                  "display": "String",
       |                  "type": "TypedClass",
       |                  "refClazzName": "java.lang.String",
       |                  "params": []
       |                },
       |                "headers": {
       |                  "display": "Map[String,String]",
       |                  "type": "TypedClass",
       |                  "refClazzName": "java.util.Map",
       |                  "params": [
       |                    {
       |                      "display": "String",
       |                      "type": "TypedClass",
       |                      "refClazzName": "java.lang.String",
       |                      "params": []
       |                    },
       |                    {
       |                      "display": "String",
       |                      "type": "TypedClass",
       |                      "refClazzName": "java.lang.String",
       |                      "params": []
       |                    }
       |                  ]
       |                }
       |              },
       |              "refClazzName": "java.util.Map",
       |              "params": [
       |                {
       |                  "display": "Unknown",
       |                  "type": "Unknown",
       |                  "refClazzName": "java.lang.Object",
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
       |          "expression": "{\\n  \\"input\\" : {\\n    \\"name\\" : \\"\\"\\n  },\\n  \\"inputMeta\\" : {\\n    \\"key\\" : \\"\\",\\n    \\"topic\\" : \\"\\",\\n    \\"partition\\" : 0,\\n    \\"offset\\" : 0,\\n    \\"timestamp\\" : 0,\\n    \\"timestampType\\" : \\"NO_TIMESTAMP_TYPE\\",\\n    \\"headers\\" : {\\n      \\"field\\" : \\"\\"\\n    },\\n    \\"leaderEpoch\\" : 0\\n  }\\n}"
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

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    createKafkaTopics()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    MockableDeploymentManager.clean()
  }

  "The endpoint for adhoc validate should" - {
    "return no errors on valid parameters" in {
      shouldValidateParametersProperly()
    }
    "return errors if passed parameter is not valid" in {
      shouldReturnErrorsForInvalidParameters()
    }
  }

  "The endpoint for adhoc test run should" - {
    "run scenario and return result" in {
      shouldProperlyRunAdHocTest()
    }
  }

  "The endpoint for adhoc test parameters should" - {
    "return test parameters" in {
      shouldProperlyGetTestParameters()
    }
  }

  "The endpoint for process validation should" - {
    "validate scenario properly with Json data and return proper typing" in {
      val typedJsonDataSamples = List("{}", "[]", "null").map(getScenarioWithDataSample)
      typedJsonDataSamples.foreach { scenarioWithEmptyDataSample =>
        val request =
          ScenarioValidationRequest(
            scenarioWithEmptyDataSample.name,
            scenarioWithEmptyDataSample.toScenarioGraph
          ).asJson.toString()

        val response = given()
          .applicationState {
            createSavedScenario(scenarioWithEmptyDataSample)
          }
          .when()
          .basicAuthAllPermUser()
          .jsonBody(request)
          .post(s"$nuDesignerHttpAddress/api/processValidation/${scenarioWithEmptyDataSample.name}")
          .getBody
          .asString()

        val typingResult = getTypingResultFromValidationResponse(response)
        typingResult("input") shouldBe Typed.json
        typingResult(variablesNodeName) match {
          case TypedObjectTypingResult(fields, _, _) =>
            fields(nameVariable) shouldBe Typed.json
            fields(ageVariable) shouldBe Typed.typedClass[Int]
            fields(isAdultVariable) shouldBe Typed.typedClass[Boolean]
          case _ => fail
        }
      }
    }
  }

  "The endpoint for test data generation should" - {
    "return error if no live data available" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(
          testDataGenerationRequest(
            exampleScenario.toScenarioGraph.asJson.spaces2,
            numberOfSamples = 3
          )
        )
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${exampleScenario.name}/generatedTestData")
        .Then()
        .statusCode(404)
        .equalsPlainBody(
          "No live test data available. Please ensure that the storage used by source contains at least one data sample"
        )
    }
  }

  "The endpoint for test with live data should" - {
    "return error if no live data available" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(
          ScenarioTestValidationRequest(
            testData = ScenarioTestData.WithLiveData(10),
            scenarioGraph = exampleScenario.toScenarioGraph
          ).asJson.toString()
        )
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${exampleScenario.name}/performTest")
        .Then()
        .statusCode(404)
        .equalsPlainBody(
          "No live test data available. Please ensure that the storage used by source contains at least one data sample"
        )
    }
  }

  "The endpoint for test with custom test data should" - {
    "perform a test on given data" in {
      val testDataContent =
        s"""[
           |  { "sourceId":"$exampleScenarioSourceId","variables": { "input": {"name": "Foo"}, "inputMeta": {"timestamp": 123} } }
           |]""".stripMargin
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .multiPart("scenarioGraph", exampleScenario.toScenarioGraph.asJson.noSpaces)
        .multiPart("testData", testDataContent)
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${exampleScenario.name}/performTest")
        .Then()
        .statusCode(200)
        .body(s"counts.$exampleScenarioSourceId.all", equalTo(1))
    }
  }

  override def designerRawConfig: Config = super.designerRawConfig
    .withoutPath("scenarioTypes.streaming.modelConfig.components.kafka.disabled")
    .withValue(
      "scenarioTypes.streaming.modelConfig.components.kafka.config.kafkaProperties",
      fromMap(Map("bootstrap.servers" -> hostKafkaAddress).asJava)
    )
    .withValue(
      "scenarioTypes.streaming.modelConfig.components.kafka.config.useDataSampleParamForSchemalessJsonTopicBasedKafkaSource",
      ConfigValueFactory.fromAnyRef(true)
    )

  private def createKafkaTopics(): Unit = {
    val sourceTopic = new NewTopic(sourceTopicName, Collections.emptyMap())
    val sinkTopic   = new NewTopic(sinkTopicName, Collections.emptyMap())
    KafkaUtils.usingAdminClient(defaultKafkaConfig) {
      _.createTopics(List(sourceTopic, sinkTopic).asJava)
    }
  }

  private def getTypingResultFromValidationResponse(jsonString: String): Map[String, TypingResult] = {
    val decoder                                             = new TypingResultDecoder(getClass.getClassLoader.loadClass)
    implicit val typingResultDecoder: Decoder[TypingResult] = decoder.decodeTypingResults

    val parsed = for {
      json        <- parse(jsonString)
      nodeResults <- json.hcursor.downField("nodeResults").as[JsonObject]
    } yield {
      nodeResults.toMap.flatMap { case (_, nodeJson) =>
        val cursor = nodeJson.hcursor.downField("variableTypes")
        cursor.keys.getOrElse(Nil).map { key =>
          key -> cursor.downField(key).focus.getOrElse(Json.Null)
        }
      }
    }

    parsed
      .getOrElse(throw new IllegalStateException("Could not parse validation response"))
      .map { case (name, jsonValue) =>
        val result = typingResultDecoder
          .decodeJson(jsonValue)
          .getOrElse(throw new IllegalStateException("Could not parse typing result"))
        name -> result
      }
  }

  private def testDataGenerationRequest(
      scenarioGraphStr: String,
      numberOfSamples: Int,
  ) =
    s"""{
       |  "scenarioGraph": $scenarioGraphStr,
       |  "numberOfSamples": $numberOfSamples
       |}""".stripMargin

  private def getScenarioWithDataSample(dataSample: String) =
    ScenarioBuilder
      .streaming(UUID.randomUUID().toString)
      .parallelism(1)
      .additionalFields(properties = Map("environment" -> "someNotEmptyString"))
      .source(
        "start",
        "kafka",
        KafkaUniversalComponentTransformer.topicParamName.value       -> s"'$sourceTopicName'".spel,
        KafkaUniversalComponentTransformer.contentTypeParamName.value -> s"'${ContentTypes.JSON.toString}'".spel,
        KafkaUniversalComponentTransformer.dataSampleParamName.value  -> Expression.json(dataSample)
      )
      .buildVariable(
        "bv1",
        variablesNodeName,
        nameVariable    -> "#input[0]['name']".spel,
        ageVariable     -> "#input[0]['age'].toInteger()".spel,
        isAdultVariable -> "#input[0]['age'].toInteger() >= 18".spel
      )
      .emptySink(
        "end",
        "kafka",
        KafkaUniversalComponentTransformer.sinkKeyParamName.value            -> "".spel,
        KafkaUniversalComponentTransformer.sinkRawEditorParamName.value      -> "true".spel,
        KafkaUniversalComponentTransformer.sinkValueParamName.value          -> "#input".spel,
        KafkaUniversalComponentTransformer.topicParamName.value              -> s"'$sinkTopicName'".spel,
        KafkaUniversalComponentTransformer.contentTypeParamName.value        -> s"'${ContentTypes.JSON.toString}'".spel,
        KafkaUniversalComponentTransformer.sinkValidationModeParamName.value -> s"'${ValidationMode.lax.name}'".spel
      )

}
