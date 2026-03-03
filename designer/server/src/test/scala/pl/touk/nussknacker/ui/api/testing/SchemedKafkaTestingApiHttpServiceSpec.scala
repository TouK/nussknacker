package pl.touk.nussknacker.ui.api.testing

import com.dimafeng.testcontainers.{Container, ForAllTestContainer, MultipleContainers}
import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromMap
import com.typesafe.scalalogging.LazyLogging
import io.circe.syntax.EncoderOps
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.apache.avro.SchemaBuilder
import org.apache.avro.generic.GenericRecordBuilder
import org.hamcrest.Matchers.equalTo
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.test.docker.{WithKafkaContainer, WithSchemaRegistryContainer}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.kafka.{KafkaClient, KafkaComponentsConfig, UnspecializedTopicName}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaId
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.test.{
  NuRestAssureExtensions,
  PatientScalaFutures,
  RestAssuredVerboseLoggingIfValidationFails
}
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig
import pl.touk.nussknacker.test.containers.WithDockerContainers
import pl.touk.nussknacker.test.processes.WithScenarioActivitySpecAsserts.UsersBasicAuth
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.TestSourceParameters
import pl.touk.nussknacker.ui.process.test.testdataformat.CommonDataFormatHandler.InputVariablesParameterName

import java.time.Instant
import scala.jdk.CollectionConverters._

class SchemedKafkaTestingApiHttpServiceSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithSimplifiedConfigScenarioHelper
    with RestAssuredVerboseLoggingIfValidationFails
    with PatientScalaFutures
    with WithAdHocTestsLogic
    with WithDockerContainers
    with WithKafkaContainer
    with WithSchemaRegistryContainer
    with ForAllTestContainer
    with LazyLogging
    with NuRestAssureExtensions
    with Matchers {

  // boostrap logic

  override val container: Container = MultipleContainers(kafkaContainer, schemaRegistryContainer)

  override def designerRawConfig: Config = super.designerRawConfig
    .withoutPath("scenarioTypes.streaming.modelConfig.components.kafka.disabled")
    .withValue(
      "scenarioTypes.streaming.modelConfig.components.kafka.config.kafkaProperties",
      fromMap(Map("bootstrap.servers" -> hostKafkaAddress, "schema.registry.url" -> hostSchemaRegistryUrl).asJava)
    )

  private val sourceTopicName = "source-topic"

  private val sourceTopicAvroSchema = SchemaBuilder
    .record("SampleRecord")
    .fields()
    .name("name")
    .`type`()
    .stringType()
    .noDefault()
    .endRecord()

  private var sourceTopicAvroSchemaId: SchemaId = _

  private lazy val schemaRegistryClient = new CachedSchemaRegistryClient(hostSchemaRegistryUrl, 10)

  private lazy val kafkaClient = new KafkaClient(hostKafkaAddress, getClass.getSimpleName)

  override protected def beforeAll(): Unit = {
    kafkaClient.createTopic(sourceTopicName, partitions = 1)
    registerSourceSchema()
    super.beforeAll()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    schemaRegistryClient.close()
    kafkaClient.shutdown()
  }

  private def registerSourceSchema(): Unit = {
    val subject = ConfluentUtils.topicSubject(new UnspecializedTopicName(sourceTopicName), isKey = false)
    sourceTopicAvroSchemaId = SchemaId.fromInt(
      schemaRegistryClient.register(subject, ConfluentUtils.convertToAvroSchema(sourceTopicAvroSchema))
    )
  }

  // tests logic

  override protected val exampleScenarioSourceId: String = "start"

  override protected val exampleScenario: CanonicalProcess = {
    ScenarioBuilder
      .streaming("without-schema")
      .parallelism(1)
      .source(
        exampleScenarioSourceId,
        "kafka",
        "Topic"          -> s"'$sourceTopicName'".spel,
        "Schema version" -> "'latest'".spel
      )
      // We add filtering logic to ensure that types are correctly verified during testing
      .filter("filter", "#input.name != 'asdf'".spel)
      .emptySink("end", "dead-end")
  }

  private val sampleAvroRecord =
    new GenericRecordBuilder(sourceTopicAvroSchema)
      .set("name", "Foo")
      .build()

  private val givenTimestamp = 123

  protected def expectedTestDataJson: String =
    s"""[
      |  {
      |    "sourceId": "$exampleScenarioSourceId",
      |    "variables": {
      |      "input": {
      |        "name": "Foo"
      |      },
      |      "inputMeta": {
      |        "timestamp": $givenTimestamp,
      |        "partition": 0,
      |        "timestampType": "CREATE_TIME",
      |        "key": null,
      |        "offset": 0,
      |        "leaderEpoch": 0,
      |        "topic": "$sourceTopicName",
      |        "headers": {}
      |      }
      |    },
      |    "upstreamTimestamp": "${Instant.ofEpochMilli(givenTimestamp)}"
      |  }
      |]""".stripMargin

  private val validJson =
    """{
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

  override protected val validParameters: TestSourceParameters =
    TestSourceParameters(exampleScenarioSourceId, Map(InputVariablesParameterName -> Expression.json(validJson)))

  override protected def expectedTestParametersJson: String =
    s"""[
       |  {
       |    "sourceId": "$exampleScenarioSourceId",
       |    "sourceName": "$exampleScenarioSourceId",
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
       |        "label": "Input variables",
       |        "requiredParam": true,
       |        "category": "Standard",
       |        "changesCanReloadParameters": false,
       |        "nonImportantForExecution": false
       |      }
       |    ]
       |  }
       |]""".stripMargin

  "The endpoint for adhoc test parameters should" - {
    "return test parameters" in {
      shouldProperlyGetTestParameters()
    }
  }

  "The endpoint for adhoc validate should" - {
    "return no errors on valid parameters" in {
      shouldValidateParametersProperly()
    }
  }

  "The endpoint for adhoc test run should" - {
    "run scenario and return result" in {
      shouldProperlyRunAdHocTest()
    }
  }

  "The endpoint for test data generation should" - {
    "generate test data" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
          kafkaClient
            .sendRawMessage(
              topic = sourceTopicName,
              key = null,
              content = ConfluentUtils.serializeContainerToBytesArray(sampleAvroRecord, sourceTopicAvroSchemaId),
              timestamp = givenTimestamp
            )
            .futureValue
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(
          testDataGenerationRequest(
            exampleScenario.toScenarioGraph.asJson.spaces2,
            numberOfSamples = 1
          )
        )
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${exampleScenario.name}/generatedTestData")
        .Then()
        .statusCode(200)
        .equalsJsonBody(expectedTestDataJson)
    }
  }

  "The endpoint for running tests from file should" - {
    "properly parse file and run tests" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .multiPart("scenarioGraph", exampleScenario.toScenarioGraph.asJson.noSpaces)
        .multiPart("testData", expectedTestDataJson)
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${exampleScenario.name}/performTest")
        .Then()
        .statusCode(200)
        .body(
          s"counts.$exampleScenarioSourceId.all",
          equalTo(1),
          "counts.end.all",
          equalTo(1)
        )
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

}
