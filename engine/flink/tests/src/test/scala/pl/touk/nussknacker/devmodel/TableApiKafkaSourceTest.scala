package pl.touk.nussknacker.devmodel

import com.typesafe.config.{Config, ConfigFactory}
import io.circe.Json
import io.confluent.kafka.schemaregistry.json.JsonSchema
import pl.touk.nussknacker.defaultmodel.{FlinkWithKafkaSuite, TopicConfig}
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.table.TableComponentProvider
import pl.touk.nussknacker.engine.flink.table.sink.SqlTableSinkFactory
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.spel

import java.nio.file.Files

class TableApiKafkaSourceTest extends FlinkWithKafkaSuite {

  import spel.Implicits._

  private val schema = new JsonSchema("""{
                                        |  "type": "object",
                                        |  "properties": {
                                        |    "someInt" : { "type": "integer" },
                                        |    "someString" : { "type": "string" }
                                        |  }
                                        |}
                                        |""".stripMargin)

  private val record1 =
    """{
      |  "someInt": 1,
      |  "someString": "AAA"
      |}""".stripMargin

  private val record2 =
    """{
      |  "someInt": 2,
      |  "someString": "BBB"
      |}""".stripMargin

  private val testTopicPart1: String    = "table-api.topic1"
  private val testTopicPart2: String    = "table-api.topic2"
  private val testTopicPart3: String    = "table-api.topic3"
  private lazy val inputTopicNameTest1  = TopicConfig.inputTopicName(testTopicPart1)
  private lazy val inputTopicNameTest2  = TopicConfig.inputTopicName(testTopicPart2)
  private lazy val outputTopicNameTest2 = TopicConfig.outputTopicName(testTopicPart2)
  private lazy val inputTopicNameTest3  = TopicConfig.inputTopicName(testTopicPart3)
  private lazy val outputTopicNameTest3 = TopicConfig.outputTopicName(testTopicPart3)

  private lazy val sqlTablesConfig =
    s"""
       |CREATE TABLE table1
       | (
       |   someInt     INT,
       |   someString  STRING
       | ) WITH (
       |  'connector' = 'kafka',
       |  'topic' = '$inputTopicNameTest3',
       |  'properties.bootstrap.servers' = '${kafkaServer.kafkaAddress}',
       |  'properties.group.id' = 'someConsumerGroupId',
       |  'scan.startup.mode' = 'earliest-offset',
       |  'format' = 'json'
       |);
       |
       |CREATE TABLE table2
       | (
       |   someInt     INT,
       |   someString  STRING
       | ) WITH (
       |  'connector' = 'kafka',
       |  'topic' = '$outputTopicNameTest3',
       |  'properties.bootstrap.servers' = '${kafkaServer.kafkaAddress}',
       |  'properties.group.id' = 'someConsumerGroupId',
       |  'scan.startup.mode' = 'earliest-offset',
       |  'format' = 'json'
       |
       |);""".stripMargin

  // TODO: how else to handle this?
  private lazy val sqlTablesDefinitionFilePath = {
    val tempDir  = Files.createTempDirectory("sqlConfigTemp")
    val filePath = tempDir.resolve("tables-definition-test.sql")
    Files.writeString(filePath, sqlTablesConfig)
    filePath
  }

  private lazy val kafkaTableConfig =
    s"""
       | sqlFilePath: $sqlTablesDefinitionFilePath
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
    val topics = createAndRegisterTopicConfig(testTopicPart1, schema)

    sendAsJson(record1, topics.input)
    sendAsJson(record2, topics.input)

    val scenarioId = "scenarioId"
    val sourceId   = "input"
    val process = ScenarioBuilder
      .streaming(scenarioId)
      .parallelism(1)
      .source(sourceId, "tableApi-source-kafka-input-test1")
      .filter("filterId", "#input.someInt != 1")
      .emptySink(
        "output",
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
    val topics = createAndRegisterTopicConfig(testTopicPart2, schema)

    sendAsJson(record1, topics.input)
    sendAsJson(record2, topics.input)

    val scenarioId = "scenarioId"
    val sourceId   = "input"
    val process = ScenarioBuilder
      .streaming(scenarioId)
      .parallelism(1)
      .source(sourceId, "tableApi-source-kafka-input-test2")
      .filter("filterId", "#input.someInt != 1")
      .emptySink(
        "end",
        "tableApi-sink-kafka-output-test2",
        SqlTableSinkFactory.rawValueParamName -> "#input"
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

  test("should ping-pong with sql kafka source and dataStream kafka sink") {
    val topics = createAndRegisterTopicConfig(testTopicPart3, schema)

    sendAsJson(record1, topics.input)
    sendAsJson(record2, topics.input)

    val scenarioId = "scenarioId"
    val sourceId   = "input"
    val process = ScenarioBuilder
      .streaming(scenarioId)
      .parallelism(1)
      .source(sourceId, "tableApi-source-sql-kafka-table1")
      .filter("filterId", "#input.someInt != 1")
      .emptySink(
        "output",
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

  test("should ping-pong with sql kafka source and sql kafka sink") {
    val topics = createAndRegisterTopicConfig(testTopicPart3, schema)

    sendAsJson(record1, topics.input)
    sendAsJson(record2, topics.input)

    val scenarioId = "scenarioId"
    val sourceId   = "input"
    val process = ScenarioBuilder
      .streaming(scenarioId)
      .parallelism(1)
      .source(sourceId, "tableApi-source-sql-kafka-table1")
      .filter("filterId", "#input.someInt != 1")
      .emptySink(
        "end",
        "tableApi-sink-sql-kafka-table2",
        SqlTableSinkFactory.rawValueParamName -> "#input"
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
