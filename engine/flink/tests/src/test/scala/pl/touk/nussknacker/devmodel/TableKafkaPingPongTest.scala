package pl.touk.nussknacker.devmodel

import com.typesafe.config.{Config, ConfigFactory}
import io.circe.Json
import io.confluent.kafka.schemaregistry.json.JsonSchema
import org.apache.commons.io.FileUtils
import pl.touk.nussknacker.defaultmodel.{FlinkWithKafkaSuite, TopicConfig}
import pl.touk.nussknacker.devmodel.TestData._
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.table.FlinkTableComponentProvider
import pl.touk.nussknacker.engine.flink.table.utils.TableComponentFactory
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaVersionOption

import java.io.File
import java.nio.charset.StandardCharsets

class TableKafkaPingPongTest extends FlinkWithKafkaSuite {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  private val testNameTopicPart: String    = "table-ping-pong"
  private val topicNaming1: String         = s"$testNameTopicPart.test1"
  private val topicNaming2: String         = s"$testNameTopicPart.test2"
  private val topicNaming3: String         = s"$testNameTopicPart.test3"
  private lazy val inputTopicNameTest1     = TopicConfig.inputTopicName(topicNaming1)
  private lazy val inputTopicNameTest2     = TopicConfig.inputTopicName(topicNaming2)
  private lazy val outputTopicNameTest2    = TopicConfig.outputTopicName(topicNaming2)
  private lazy val inputTopicNameTest3     = TopicConfig.inputTopicName(topicNaming3)
  private lazy val outputTopicNameTest3    = TopicConfig.outputTopicName(topicNaming3)
  private lazy val sqlInputTableNameTest1  = "input_test1"
  private lazy val sqlInputTableNameTest2  = "input_test2"
  private lazy val sqlOutputTableNameTest2 = "output_test2"
  private lazy val sqlInputTableNameTest3  = "input_test3"
  private lazy val sqlOutputTableNameTest3 = "output_test3"
  private val tableComponentName           = "table"

  private lazy val sqlTablesConfig =
    s"""
       |CREATE TABLE $sqlInputTableNameTest1 (
       |   someInt     INT,
       |   someString  STRING
       | ) WITH (
       |  'connector' = 'kafka',
       |  'topic' = '${inputTopicNameTest1.name}',
       |  'properties.bootstrap.servers' = '${kafkaServer.kafkaAddress}',
       |  'properties.group.id' = 'someConsumerGroupId',
       |  'scan.startup.mode' = 'earliest-offset',
       |  'format' = 'json'
       |);
       |
       |CREATE TABLE $sqlInputTableNameTest2
       | (
       |   someInt     INT,
       |   someString  STRING
       | ) WITH (
       |  'connector' = 'kafka',
       |  'topic' = '${inputTopicNameTest2.name}',
       |  'properties.bootstrap.servers' = '${kafkaServer.kafkaAddress}',
       |  'properties.group.id' = 'someConsumerGroupId',
       |  'scan.startup.mode' = 'earliest-offset',
       |  'format' = 'json'
       |);
       |
       |CREATE TABLE $sqlOutputTableNameTest2
       | (
       |   someInt     INT,
       |   someString  STRING
       | ) WITH (
       |  'connector' = 'kafka',
       |  'topic' = '${outputTopicNameTest2.name}',
       |  'properties.bootstrap.servers' = '${kafkaServer.kafkaAddress}',
       |  'properties.group.id' = 'someConsumerGroupId',
       |  'scan.startup.mode' = 'earliest-offset',
       |  'format' = 'json'
       |);
       |
       |CREATE TABLE $sqlInputTableNameTest3
       | (
       |   someInt     INT,
       |   someString  STRING
       | ) WITH (
       |  'connector' = 'kafka',
       |  'topic' = '${inputTopicNameTest3.name}',
       |  'properties.bootstrap.servers' = '${kafkaServer.kafkaAddress}',
       |  'properties.group.id' = 'someConsumerGroupId',
       |  'scan.startup.mode' = 'earliest-offset',
       |  'format' = 'json'
       |);
       |
       |CREATE TABLE $sqlOutputTableNameTest3
       | (
       |   someInt     INT,
       |   someString  STRING
       | ) WITH (
       |  'connector' = 'kafka',
       |  'topic' = '${outputTopicNameTest3.name}',
       |  'properties.bootstrap.servers' = '${kafkaServer.kafkaAddress}',
       |  'properties.group.id' = 'someConsumerGroupId',
       |  'scan.startup.mode' = 'earliest-offset',
       |  'format' = 'json'
       |);
       |""".stripMargin

  private lazy val sqlTablesDefinitionFilePath = {
    val tempFile = File.createTempFile("tables-definition-test", ".sql")
    tempFile.deleteOnExit()
    FileUtils.writeStringToFile(tempFile, sqlTablesConfig, StandardCharsets.UTF_8)
    tempFile.toPath
  }

  private lazy val kafkaTableConfig =
    s"""
       |{
       |  tableDefinitionFilePath: $sqlTablesDefinitionFilePath
       |}
       |""".stripMargin

  private lazy val tableKafkaComponentsConfig: Config = ConfigFactory.parseString(kafkaTableConfig)

  override lazy val additionalComponents: List[ComponentDefinition] = new FlinkTableComponentProvider().create(
    tableKafkaComponentsConfig,
    ProcessObjectDependencies.withConfig(tableKafkaComponentsConfig)
  )

  test("should ping-pong with sql kafka source and DataStream kafka sink") {
    val topics = createAndRegisterTopicConfig(topicNaming1, simpleTypesSchema)

    sendAsJson("""{"someInt": 1, "someString": "AAA"}""", topics.input)
    sendAsJson("""{"someInt": 2, "someString": "BBB"}""", topics.input)

    val process = ScenarioBuilder
      .streaming("testScenario")
      .parallelism(1)
      .source(
        "start",
        tableComponentName,
        TableComponentFactory.tableNameParamName.value -> s"'$sqlInputTableNameTest1'".spel
      )
      .filter("filterId", "#input.someInt != 1".spel)
      .emptySink(
        "output",
        "kafka",
        KafkaUniversalComponentTransformer.sinkKeyParamName.value   -> "".spel,
        KafkaUniversalComponentTransformer.sinkValueParamName.value -> "#input".spel,
        KafkaUniversalComponentTransformer.topicParamName.value     -> s"'${topics.output.name}'".spel,
        KafkaUniversalComponentTransformer.schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel,
        KafkaUniversalComponentTransformer.sinkRawEditorParamName.value -> "true".spel,
      )

    run(process) {
      val result = kafkaClient
        .createConsumer()
        .consumeWithJson[Json](topics.output.name)
        .take(1)
        .map(_.message())

      result.head shouldBe parseJson("""{"someInt": 2, "someString": "BBB"}""")
    }
  }

  test("should ping-pong with sql kafka source and sql kafka sink") {
    val topics = createAndRegisterTopicConfig(topicNaming2, simpleTypesSchema)

    sendAsJson("""{"someInt": 1, "someString": "AAA"}""", topics.input)
    sendAsJson("""{"someInt": 2, "someString": "BBB"}""", topics.input)

    val scenarioId = "scenarioId"
    val sourceId   = "input"
    val process = ScenarioBuilder
      .streaming(scenarioId)
      .parallelism(1)
      .source(
        sourceId,
        tableComponentName,
        TableComponentFactory.tableNameParamName.value -> s"'$sqlInputTableNameTest2'".spel
      )
      .filter("filterId", "#input.someInt != 1".spel)
      .emptySink(
        "end",
        tableComponentName,
        "Table"      -> s"'$sqlOutputTableNameTest2'".spel,
        "Raw editor" -> "true".spel,
        "Value"      -> "#input".spel
      )

    run(process) {
      val result = kafkaClient
        .createConsumer()
        .consumeWithJson[Json](topics.output.name)
        .take(1)
        .map(_.message())

      result.head shouldBe parseJson("""{"someInt": 2, "someString": "BBB"}""")
    }
  }

  test("should pong with explicit spel record and DataStream kafka sink") {
    val topics = createAndRegisterTopicConfig(topicNaming3, simpleTypesSchema)

    sendAsJson("""{"someInt": 1, "someString": "AAA"}""", topics.input)

    val process = ScenarioBuilder
      .streaming("testScenario")
      .parallelism(1)
      .source(
        "start",
        tableComponentName,
        TableComponentFactory.tableNameParamName.value -> s"'$sqlInputTableNameTest3'".spel
      )
      .emptySink(
        "end",
        tableComponentName,
        "Table"      -> s"'$sqlOutputTableNameTest3'".spel,
        "Raw editor" -> "true".spel,
        "Value"      -> "{someInt: 2, someString: 'BBB'}".spel
      )

    run(process) {
      val result = kafkaClient
        .createConsumer()
        .consumeWithJson[Json](topics.output.name)
        .take(1)
        .map(_.message())

      result.head shouldBe parseJson("""{"someInt": 2, "someString": "BBB"}""")
    }

  }

}

object TestData {

  val simpleTypesSchema: JsonSchema = new JsonSchema(
    """{
      |  "type": "object",
      |  "properties": {
      |    "someInt" : { "type": "integer" },
      |    "someString" : { "type": "string" }
      |  }
      |}
      |""".stripMargin
  )

}
