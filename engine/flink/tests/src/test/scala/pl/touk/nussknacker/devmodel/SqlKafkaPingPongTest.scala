package pl.touk.nussknacker.devmodel

import com.typesafe.config.{Config, ConfigFactory}
import io.circe.Json
import pl.touk.nussknacker.defaultmodel.{FlinkWithKafkaSuite, TopicConfig}
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.table.{SqlComponentProvider, TableComponentProvider}
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.spel
import TestData._

import java.nio.file.Files

class SqlKafkaPingPongTest extends FlinkWithKafkaSuite {

  import spel.Implicits._

  private val testNameTopicPart: String = "sql-pp"
  private val topicNaming1: String      = s"$testNameTopicPart.test1"
  private lazy val inputTopicNameTest1  = TopicConfig.inputTopicName(topicNaming1)

  private lazy val sqlTablesConfig =
    s"""
       |CREATE TABLE testTable (
       |   someInt     INT,
       |   someString  STRING
       | ) WITH (
       |  'connector' = 'kafka',
       |  'topic' = '$inputTopicNameTest1',
       |  'properties.bootstrap.servers' = '${kafkaServer.kafkaAddress}',
       |  'properties.group.id' = 'someConsumerGroupId',
       |  'scan.startup.mode' = 'earliest-offset',
       |  'format' = 'json'
       |);""".stripMargin

  private lazy val sqlTablesDefinitionFilePath = {
    val tempDir  = Files.createTempDirectory("sqlConfigTemp")
    val filePath = tempDir.resolve("tables-definition-test.sql")
    Files.writeString(filePath, sqlTablesConfig)
    filePath
  }

  private lazy val kafkaTableConfig = s"sqlFilePath: $sqlTablesDefinitionFilePath"

  private lazy val tableKafkaComponentsConfig: Config = ConfigFactory.parseString(kafkaTableConfig)

  override lazy val additionalComponents: List[ComponentDefinition] = new SqlComponentProvider().create(
    tableKafkaComponentsConfig,
    ProcessObjectDependencies.withConfig(tableKafkaComponentsConfig)
  )

  test("should ping-pong with sql kafka source and dataStream kafka sink") {
    val topics = createAndRegisterTopicConfig(topicNaming1, simpleTypesSchema)

    sendAsJson(record1, topics.input)
    sendAsJson(record2, topics.input)

    val process = ScenarioBuilder
      .streaming("testScenario")
      .parallelism(1)
      .source("start", "tableApi-source-sql")
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

}
