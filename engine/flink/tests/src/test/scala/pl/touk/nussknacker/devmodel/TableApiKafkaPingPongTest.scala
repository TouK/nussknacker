package pl.touk.nussknacker.devmodel

import com.typesafe.config.{Config, ConfigFactory}
import io.circe.Json
import io.confluent.kafka.schemaregistry.json.JsonSchema
import pl.touk.nussknacker.defaultmodel.{FlinkWithKafkaSuite, TopicConfig}
import pl.touk.nussknacker.devmodel.TestData.{record1, record2, simpleTypesSchema}
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.table.TableComponentProvider
import pl.touk.nussknacker.engine.flink.table.sink.TableSinkFactory
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.spel

class TableApiKafkaSourceTest extends FlinkWithKafkaSuite {

  import spel.Implicits._

  private val testNameTopicPart: String = "table-api-pp"
  private val topicNaming1: String      = s"$testNameTopicPart.test1"
  private val topicNaming2: String      = s"$testNameTopicPart.test2"
  private lazy val inputTopicNameTest1  = TopicConfig.inputTopicName(topicNaming1)
  private lazy val inputTopicNameTest2  = TopicConfig.inputTopicName(topicNaming2)
  private lazy val outputTopicNameTest2 = TopicConfig.outputTopicName(topicNaming2)

  private lazy val kafkaTableConfig =
    s"""
       | dataSources: [
       |   {
       |     name: "input-test1"
       |     connector: "kafka"
       |     format: "json"
       |     options {
       |       "properties.bootstrap.servers": "${kafkaServer.kafkaAddress}"
       |       "properties.group.id": "someConsumerGroupId"
       |       "scan.startup.mode": "earliest-offset"
       |       "topic": "$inputTopicNameTest1"
       |     }
       |   },
       |   {
       |     name: "input-test2"
       |     connector: "kafka"
       |     format: "json"
       |     options {
       |       "properties.bootstrap.servers": "${kafkaServer.kafkaAddress}"
       |       "properties.group.id": "someConsumerGroupId"
       |       "scan.startup.mode": "earliest-offset"
       |       "topic": "$inputTopicNameTest2"
       |     }
       |   },
       |   {
       |     name: "output-test2"
       |     connector: "kafka"
       |     format: "json"
       |     options {
       |       "properties.bootstrap.servers": "${kafkaServer.kafkaAddress}"
       |       "properties.group.id": "someConsumerGroupId"
       |       "scan.startup.mode": "earliest-offset"
       |       "topic": "$outputTopicNameTest2"
       |     }
       |   },
       | ]
       |""".stripMargin

  private lazy val tableKafkaComponentsConfig: Config = ConfigFactory.parseString(kafkaTableConfig)

  override lazy val additionalComponents: List[ComponentDefinition] = new TableComponentProvider().create(
    tableKafkaComponentsConfig,
    ProcessObjectDependencies.withConfig(tableKafkaComponentsConfig)
  )

  test("should ping-pong with table kafka source and dataStream kafka sink") {
    val topics = createAndRegisterTopicConfig(topicNaming1, simpleTypesSchema)

    sendAsJson(record1, topics.input)
    sendAsJson(record2, topics.input)

    val process = ScenarioBuilder
      .streaming("testScenario")
      .parallelism(1)
      .source("start", "tableApi-source-kafka-input-test1")
      .filter("filter", "#input.someInt != 1")
      .emptySink(
        "end",
        "kafka",
        KafkaUniversalComponentTransformer.SinkKeyParamName       -> "",
        KafkaUniversalComponentTransformer.SinkValueParamName     -> "#input",
        KafkaUniversalComponentTransformer.TopicParamName         -> s"'${topics.output}'",
        KafkaUniversalComponentTransformer.SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'",
        KafkaUniversalComponentTransformer.SinkRawEditorParamName -> s"true",
      )

    run(process) {
      val result = kafkaClient
        .createConsumer()
        .consumeWithJson[Json](topics.output)
        .take(1)
        .map(_.message())

      result.head shouldBe parseJson(record2)
    }
  }

  test("should ping-pong with table kafka source and table kafka sink") {
    val topics = createAndRegisterTopicConfig(topicNaming2, simpleTypesSchema)

    sendAsJson(record1, topics.input)
    sendAsJson(record2, topics.input)

    val process = ScenarioBuilder
      .streaming("testScenario")
      .parallelism(1)
      .source("start", "tableApi-source-kafka-input-test2")
      .filter("filter", "#input.someInt != 1")
      .emptySink(
        "end",
        "tableApi-sink-kafka-output-test2",
        TableSinkFactory.rawValueParamName -> "#input"
      )

    run(process) {
      val result = kafkaClient
        .createConsumer()
        .consumeWithJson[Json](topics.output)
        .take(1)
        .map(_.message())

      result.head shouldBe parseJson(record2)
    }
  }

}

object TestData {

  val simpleTypesSchema: JsonSchema = new JsonSchema("""{
      |  "type": "object",
      |  "properties": {
      |    "someInt" : { "type": "integer" },
      |    "someString" : { "type": "string" }
      |  }
      |}
      |""".stripMargin)

  val record1: String =
    """{
      |  "someInt": 1,
      |  "someString": "AAA"
      |}""".stripMargin

  val record2: String =
    """{
      |  "someInt": 2,
      |  "someString": "BBB"
      |}""".stripMargin

}
