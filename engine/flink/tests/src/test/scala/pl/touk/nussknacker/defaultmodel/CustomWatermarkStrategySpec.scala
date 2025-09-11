package pl.touk.nussknacker.defaultmodel

import com.typesafe.config.{Config, ConfigValueFactory}
import io.circe.Json
import pl.touk.nussknacker.engine.api.process.TopicName.ForSource
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.ContentTypes
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.util.watermarkstrategy.WatermarkStrategyValidationHandler

import java.time.Instant

class CustomWatermarkStrategySpec extends FlinkWithKafkaSuite {

  override protected def resolveModelConfig(config: Config): Config =
    super
      .resolveModelConfig(config)
      .withValue(
        s"$kafkaComponentsConfigPrefix.useDataSampleParamForSchemalessJsonTopicBasedKafkaSource",
        ConfigValueFactory.fromAnyRef(true)
      )

  private val givenKey  = "foo-key"
  private val givenData = 1


  test("should use timestamp configured by a user in event generator") {
    val outputTopic = "output-topic-custom-event-time-event-generator"

    kafkaClient.createTopic(outputTopic, 1)
    val givenTimestamp   = 123

    val scenario =
      ScenarioBuilder
        .streaming("without-schema")
        .parallelism(1)
        .source(
          "start",
          "event-generator",
          "schedule"  -> "T(java.time.Duration).parse('PT1S')".spel,
          "value"   ->
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
    val givenFistEventTimestamp   = 0
    val givenSecondEventTimestamp = 60 * 1000 - 1
    val givenThirdEventTimestamp  = 60 * 1000
    sendEventWithTimestampOnTopic(givenFistEventTimestamp, inputTopic)
    sendEventWithTimestampOnTopic(givenSecondEventTimestamp, inputTopic)
    sendEventWithTimestampOnTopic(givenThirdEventTimestamp, inputTopic)

    val scenario =
      ScenarioBuilder
        .streaming("without-schema")
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
      firstRecord.timestamp shouldBe givenFistEventTimestamp

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
        .streaming("without-schema")
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
