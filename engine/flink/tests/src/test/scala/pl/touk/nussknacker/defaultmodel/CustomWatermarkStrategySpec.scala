package pl.touk.nussknacker.defaultmodel

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import io.circe.Json
import org.scalatest.{LoneElement, OptionValues}
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.TopicName.ForSource
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.table.FlinkTableDataSourceComponentProvider
import pl.touk.nussknacker.engine.flink.util.test.FlinkNodeCompiler.FlinkNodeCompilerExt
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.ContentTypes
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.util.test.TestNodeCompiler
import pl.touk.nussknacker.engine.util.watermarkstrategy.WatermarkStrategyValidationHandler
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable

import java.time.{Instant, ZoneId, ZoneOffset}
import java.time.format.DateTimeFormatter

class CustomWatermarkStrategySpec extends FlinkWithKafkaSuite with OptionValues with LoneElement {

  override protected def resolveModelConfig(config: Config): Config =
    super
      .resolveModelConfig(config)
      .withValue(
        s"$kafkaComponentsConfigPrefix.useDataSampleParamForSchemalessJsonTopicBasedKafkaSource",
        ConfigValueFactory.fromAnyRef(true)
      )

  private val eventTimeConfiguredInSourceTableName = "input_topic_event_time_table_source"
  private val eventTimeConfiguredInSourceTopicName = "input-topic-event-time-table-source"

  private val eventTimeConfiguredInTableDefinitionTableName = "input_topic_event_time_table_definition"
  private val eventTimeConfiguredInTableDefinitionTopicName = "input-topic-event-time-table-definition"

  private lazy val tablesDefinition =
    s"""CREATE TABLE $eventTimeConfiguredInSourceTableName (
       |  `timestamp` TIMESTAMP_LTZ(3)
       |) WITH (
       |  'connector' = 'kafka',
       |  'topic' = '$eventTimeConfiguredInSourceTopicName',
       |  'properties.bootstrap.servers' = '${kafkaServer.bootstrapServers}',
       |  'properties.group.id' = 'custom-event-time-table-source',
       |  'scan.startup.mode' = 'earliest-offset',
       |  'format' = 'json'
       |);
       |
       |CREATE TABLE $eventTimeConfiguredInTableDefinitionTableName (
       |  `timestamp` TIMESTAMP_LTZ(3),
       |  WATERMARK FOR `timestamp` AS `timestamp`
       |) WITH (
       |  'connector' = 'kafka',
       |  'topic' = '$eventTimeConfiguredInTableDefinitionTopicName',
       |  'properties.bootstrap.servers' = '${kafkaServer.bootstrapServers}',
       |  'properties.group.id' = 'custom-event-time-table-definition',
       |  'scan.startup.mode' = 'earliest-offset',
       |  'format' = 'json'
       |);
       |""".stripMargin

  private lazy val kafkaTableConfig =
    s"""
       |{
       |  tableDefinition: \"\"\" $tablesDefinition \"\"\"
       |}
       |""".stripMargin

  override lazy val additionalComponents: List[ComponentDefinition] =
    new FlinkTableDataSourceComponentProvider().create(ConfigFactory.parseString(kafkaTableConfig))

  private val givenKey  = "foo-key"
  private val givenData = 1

  private lazy val nodeCompiler = TestNodeCompiler
    .flinkBased(modelConfig)
    .withFlinkMiniCluster(flinkMiniCluster)
    .withExtraComponents(additionalComponents)
    .build()

  test("should use timestamp configured in table definition used by table source") {
    val outputTopic = "output-topic-event-time-table-definition"

    kafkaClient.createTopic(eventTimeConfiguredInTableDefinitionTopicName, 1)
    kafkaClient.createTopic(outputTopic, 1)
    val givenTimestamp = Instant.ofEpochMilli(123)
    val jsonRecord = Json.obj(
      "timestamp" -> Json.fromString(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSX").format(givenTimestamp.atZone(ZoneOffset.UTC))
      ),
    )
    sendAsJson(jsonRecord.toString, ForSource(eventTimeConfiguredInTableDefinitionTopicName), Instant.now.toEpochMilli)

    val scenario =
      ScenarioBuilder
        .streaming("custom-event-time-table-source")
        .parallelism(1)
        .source(
          "start",
          "table",
          "Table" -> s"'`default_catalog`.`default_database`.`$eventTimeConfiguredInTableDefinitionTableName`'".spel
        )
        .emptySink(
          "end",
          "kafka",
          KafkaUniversalComponentTransformer.sinkKeyParamName.value     -> "".spel,
          KafkaUniversalComponentTransformer.sinkValueParamName.value   -> "foo".spelTemplate,
          KafkaUniversalComponentTransformer.topicParamName.value       -> s"'$outputTopic'".spel,
          KafkaUniversalComponentTransformer.contentTypeParamName.value -> s"'${ContentTypes.PLAIN.toString}'".spel,
        )

    val compilationResult = nodeCompiler.compileNode(
      node.Source(
        "id",
        SourceRef(
          "table",
          Parameter(
            ParameterName("Table"),
            s"'`default_catalog`.`default_database`.`$eventTimeConfiguredInTableDefinitionTableName`'".spel
          ) ::
            Nil
        )
      )
    )
    compilationResult.compiledObject shouldBe Symbol("valid")
    val dynamicParametersDefinitions = compilationResult.parameters.value
    val eventTimeParameterDefinition =
      dynamicParametersDefinitions.filter(_.name == WatermarkStrategyValidationHandler.eventTimeParamName).loneElement
    // Lack of Event time parameter will be interpreted as null expression which means that it will be used upstream Event time
    eventTimeParameterDefinition.defaultValue.value shouldBe "".spel

    testScenarioRunner.withRunningScenario(scenario) { _ =>
      val records = kafkaClient.createConsumer().consumeWithConsumerRecord(outputTopic)

      val firstRecord = records.head
      firstRecord.timestamp shouldBe givenTimestamp.toEpochMilli
    }
  }

  test("should use timestamp configured by a user in table source") {
    val outputTopic = "output-topic-event-time-table-source"

    kafkaClient.createTopic(eventTimeConfiguredInSourceTopicName, 1)
    kafkaClient.createTopic(outputTopic, 1)
    val givenTimestamp = Instant.ofEpochMilli(123)
    val jsonRecord = Json.obj(
      "timestamp" -> Json.fromString(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSX").format(givenTimestamp.atZone(ZoneOffset.UTC))
      ),
    )
    sendAsJson(jsonRecord.toString, ForSource(eventTimeConfiguredInSourceTopicName), Instant.now.toEpochMilli)

    val scenario =
      ScenarioBuilder
        .streaming("custom-event-time-table-source")
        .parallelism(1)
        .source(
          "start",
          "table",
          "Table"      -> s"'`default_catalog`.`default_database`.`$eventTimeConfiguredInSourceTableName`'".spel,
          "Event time" -> "#input.timestamp".spel
        )
        .emptySink(
          "end",
          "kafka",
          KafkaUniversalComponentTransformer.sinkKeyParamName.value     -> "".spel,
          KafkaUniversalComponentTransformer.sinkValueParamName.value   -> "foo".spelTemplate,
          KafkaUniversalComponentTransformer.topicParamName.value       -> s"'$outputTopic'".spel,
          KafkaUniversalComponentTransformer.contentTypeParamName.value -> s"'${ContentTypes.PLAIN.toString}'".spel,
        )

    testScenarioRunner.withRunningScenario(scenario) { _ =>
      val records = kafkaClient.createConsumer().consumeWithConsumerRecord(outputTopic)

      val firstRecord = records.head
      firstRecord.timestamp shouldBe givenTimestamp.toEpochMilli
    }
  }

  test("should return watermark strategy dynamic parameters when they are not provided") {
    nodeCompiler
      .compileNode(
        node.Source(
          "id",
          SourceRef(
            "event-generator",
            Parameter(ParameterName("schedule"), "T(java.time.Duration).parse('PT1S')".spel) ::
              Parameter(ParameterName("value"), "123".spel) ::
              Nil
          )
        )
      )
      .parameters
      .value
      .map(_.name.value) shouldBe List(
      "schedule",
      "count",
      "value",
      "Event time",
      "Max out-of-orderness"
    )
  }

  test("should use timestamp configured by a user in event generator") {
    val outputTopic = "output-topic-custom-event-time-event-generator"

    kafkaClient.createTopic(outputTopic, 1)
    val givenTimestamp = 10000000000L

    val scenario =
      ScenarioBuilder
        .streaming("custom-event-time-event-generator")
        .parallelism(1)
        .source(
          "start",
          "event-generator",
          "schedule" -> "T(java.time.Duration).parse('PT1S')".spel,
          "value" ->
            s"""{
               |  "timestamp": $givenTimestamp
               |}""".stripMargin.jsonTemplate,
          "Event time" -> "#input.timestamp".spel
        )
        .emptySink(
          "end",
          "kafka",
          KafkaUniversalComponentTransformer.sinkKeyParamName.value     -> "".spel,
          KafkaUniversalComponentTransformer.sinkValueParamName.value   -> "foo".spelTemplate,
          KafkaUniversalComponentTransformer.topicParamName.value       -> s"'$outputTopic'".spel,
          KafkaUniversalComponentTransformer.contentTypeParamName.value -> s"'${ContentTypes.PLAIN.toString}'".spel,
        )

    testScenarioRunner.withRunningScenario(scenario) { _ =>
      val records = kafkaClient.createConsumer().consumeWithConsumerRecord(outputTopic)

      val firstRecord = records.head
      firstRecord.timestamp shouldBe givenTimestamp
    }
  }

  test("should use timestamp configured by a user in kafka source") {
    val inputTopic  = "input-topic-custom-event-time-kafka-source"
    val outputTopic = "output-topic-custom-event-time-kafka-source"

    kafkaClient.createTopic(inputTopic, 1)
    kafkaClient.createTopic(outputTopic, 1)
    val givenFirstEventTimestamp  = 6000000000L
    val givenSecondEventTimestamp = 6000000000L + 60 * 1000 - 1
    val givenThirdEventTimestamp  = 6000000000L + 60 * 1000
    sendEventWithTimestampOnTopic(givenFirstEventTimestamp, inputTopic)
    sendEventWithTimestampOnTopic(givenSecondEventTimestamp, inputTopic)
    sendEventWithTimestampOnTopic(givenThirdEventTimestamp, inputTopic)

    val scenario =
      ScenarioBuilder
        .streaming("custom-event-time-kafka-source")
        .parallelism(1)
        .source(
          "start",
          "kafka",
          KafkaUniversalComponentTransformer.topicParamName.value       -> Expression.spel(s"'$inputTopic'"),
          KafkaUniversalComponentTransformer.contentTypeParamName.value -> s"'${ContentTypes.JSON.toString}'".spel,
          KafkaUniversalComponentTransformer.dataSampleParamName.value  -> dataSampleExpression,
          WatermarkStrategyValidationHandler.eventTimeParamName.value   -> "#input.timestamp".spel,
        )
        .customNode(
          "aggregate",
          "sum",
          "aggregate-sliding",
          "groupBy"      -> "#input.key".spel,
          "aggregateBy"  -> "#input.data".spel,
          "aggregator"   -> "#AGG.sum".spel,
          "windowLength" -> "T(java.time.Duration).parse('PT1M')".spel,
        )
        .emptySink(
          "end",
          "kafka",
          KafkaUniversalComponentTransformer.sinkKeyParamName.value     -> "#key".spel,
          KafkaUniversalComponentTransformer.sinkValueParamName.value   -> "#sum".spel,
          KafkaUniversalComponentTransformer.topicParamName.value       -> s"'$outputTopic'".spel,
          KafkaUniversalComponentTransformer.contentTypeParamName.value -> s"'${ContentTypes.JSON.toString}'".spel,
        )

    testScenarioRunner.withRunningScenario(scenario) { _ =>
      val parsedRecords = kafkaClient.createConsumer().consumeWithJson[Int](outputTopic).take(3)

      val firstRecord = parsedRecords.head
      firstRecord.key() shouldBe givenKey
      firstRecord.msg shouldBe givenData
      firstRecord.timestamp shouldBe givenFirstEventTimestamp

      val secondRecord = parsedRecords(1)
      secondRecord.key() shouldBe givenKey
      secondRecord.msg shouldBe givenData * 2
      secondRecord.timestamp shouldBe givenSecondEventTimestamp

      val thirdRecord = parsedRecords(2)
      thirdRecord.key() shouldBe givenKey
      thirdRecord.msg shouldBe givenData
      thirdRecord.timestamp shouldBe givenThirdEventTimestamp
    }
  }

  test("should respect max out-of-orderness specified by a user") {
    val inputTopic  = "input-topic-max-out-of-orderness"
    val outputTopic = "output-topic-max-out-of-orderness"

    kafkaClient.createTopic(inputTopic, 1)
    kafkaClient.createTopic(outputTopic, 1)
    val configuredMaxOutOfOrdenessSeconds = 5
    val anotherKey                        = "bar-key"

    val scenario =
      ScenarioBuilder
        .streaming("max-out-of-orderness")
        .parallelism(1)
        .source(
          "start",
          "kafka",
          KafkaUniversalComponentTransformer.topicParamName.value       -> Expression.spel(s"'$inputTopic'"),
          KafkaUniversalComponentTransformer.contentTypeParamName.value -> s"'${ContentTypes.JSON.toString}'".spel,
          KafkaUniversalComponentTransformer.dataSampleParamName.value  -> dataSampleExpression,
          WatermarkStrategyValidationHandler.eventTimeParamName.value   -> "#input.timestamp".spel,
          WatermarkStrategyValidationHandler.maxOutOfOrdernessParamName.value -> s"T(java.time.Duration).parse('PT${configuredMaxOutOfOrdenessSeconds}S')".spel,
        )
        .customNode(
          "aggregate",
          "sum",
          "aggregate-tumbling",
          "groupBy"      -> "#input.key".spel,
          "aggregateBy"  -> "#input.data".spel,
          "aggregator"   -> "#AGG.sum".spel,
          "windowLength" -> "T(java.time.Duration).parse('PT1M')".spel,
          "emitWhen" -> "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.TumblingWindowTrigger).OnEnd".spel,
        )
        .emptySink(
          "end",
          "kafka",
          KafkaUniversalComponentTransformer.sinkKeyParamName.value     -> "#key".spel,
          KafkaUniversalComponentTransformer.sinkValueParamName.value   -> "#sum".spel,
          KafkaUniversalComponentTransformer.topicParamName.value       -> s"'$outputTopic'".spel,
          KafkaUniversalComponentTransformer.contentTypeParamName.value -> s"'${ContentTypes.JSON.toString}'".spel,
        )

    val parsedRecords = kafkaClient.createConsumer().consumeWithJson[Int](outputTopic).filter(_.key() == givenKey)

    testScenarioRunner.withRunningScenario(scenario) { _ =>
      sendEventWithTimestampOnTopic(0, inputTopic, key = givenKey)
      sendEventWithTimestampOnTopic((60 + configuredMaxOutOfOrdenessSeconds) * 1000, inputTopic, key = anotherKey)

      val firstRecord = parsedRecords.head
      firstRecord.msg shouldBe givenData
      firstRecord.timestamp shouldBe 60 * 1000 - 1

      // late event
      sendEventWithTimestampOnTopic(60 * 1000 - 1, inputTopic, key = givenKey)

      sendEventWithTimestampOnTopic(60 * 1000, inputTopic, key = givenKey)
      sendEventWithTimestampOnTopic((2 * 60 + configuredMaxOutOfOrdenessSeconds) * 1000, inputTopic, key = anotherKey)

      val secondRecord = parsedRecords(1)
      secondRecord.msg shouldBe givenData
      secondRecord.timestamp shouldBe 2 * 60 * 1000 - 1
    }
  }

  private lazy val dataSampleExpression = {
    s"""{
       |  "key": "$givenKey",
       |  "data": $givenData,
       |  "timestamp": 0
       |}""".stripMargin.jsonExpression
  }

  private def sendEventWithTimestampOnTopic(eventTimestamp: Long, topic: String, key: String = givenKey) = {
    val jsonRecord = Json.obj(
      "key"       -> Json.fromString(key),
      "data"      -> Json.fromLong(givenData),
      "timestamp" -> Json.fromLong(eventTimestamp),
    )
    sendAsJson(jsonRecord.toString, ForSource(topic), Instant.now.toEpochMilli)
  }

}
