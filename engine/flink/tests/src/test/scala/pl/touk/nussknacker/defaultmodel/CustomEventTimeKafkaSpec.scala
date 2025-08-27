package pl.touk.nussknacker.defaultmodel

import com.typesafe.config.{Config, ConfigValueFactory}
import io.circe.Json
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.process.TopicName.ForSource
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.ContentTypes
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.util.eventtime.EventTimeValidationHandler

import java.time.Instant

class CustomEventTimeKafkaSpec extends FlinkWithKafkaSuite {

  override protected def resolveModelConfig(config: Config): Config =
    super
      .resolveModelConfig(config)
      .withValue(
        s"$kafkaComponentsConfigPrefix.useDataSampleParamForSchemalessJsonTopicBasedKafkaSource",
        ConfigValueFactory.fromAnyRef(true)
      )

  test("should use timestamp configured by a user") {
    val inputTopic  = "input-topic-custom-event-time"
    val outputTopic = "output-topic-custom-event-time"

    kafkaClient.createTopic(inputTopic, 1)
    kafkaClient.createTopic(outputTopic, 1)
    val givenKey  = "foo-key"
    val givenData = 1
    def sendEvent(eventTimestamp: Long) = {
      val jsonRecord = Json.obj(
        "key"       -> Json.fromString(givenKey),
        "data"      -> Json.fromLong(givenData),
        "timestamp" -> Json.fromLong(eventTimestamp),
      )
      sendAsJson(jsonRecord.toString, ForSource(inputTopic), Instant.now.toEpochMilli)
    }

    val givenFistEventTimestamp   = 0
    val givenSecondEventTimestamp = 60 * 1000 - 1
    val givenThirdEventTimestamp  = 60 * 1000
    sendEvent(givenFistEventTimestamp)
    sendEvent(givenSecondEventTimestamp)
    sendEvent(givenThirdEventTimestamp)

    val scenario =
      ScenarioBuilder
        .streaming("without-schema")
        .parallelism(1)
        .source(
          "start",
          "kafka",
          KafkaUniversalComponentTransformer.topicParamName.value       -> Expression.spel(s"'$inputTopic'"),
          KafkaUniversalComponentTransformer.contentTypeParamName.value -> s"'${ContentTypes.JSON.toString}'".spel,
          KafkaUniversalComponentTransformer.dataSampleParamName.value ->
            s"""{
              |  "key": "$givenKey",
              |  "data": $givenData,
              |  "timestamp": 0
              |}""".stripMargin.jsonExpression,
          EventTimeValidationHandler.eventTimeParamName.value -> "#input.timestamp".spel,
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
          KafkaUniversalComponentTransformer.sinkKeyParamName.value -> "".spel,
          KafkaUniversalComponentTransformer.sinkValueParamName.value ->
            """{
              |  "key": "#{ #input.key }",
              |  "sum": #{ #sum }
              |}""".stripMargin.jsonTemplate,
          KafkaUniversalComponentTransformer.topicParamName.value       -> s"'$outputTopic'".spel,
          KafkaUniversalComponentTransformer.contentTypeParamName.value -> s"'${ContentTypes.JSON.toString}'".spel,
        )

    testScenarioRunner.withRunningScenario(scenario) { _ =>
      val parsedRecords = kafkaClient.createConsumer().consumeWithJson[OutputRecord](outputTopic).take(3)

      val firstRecord = parsedRecords.head
      firstRecord.msg shouldBe OutputRecord(givenKey, givenData)
      firstRecord.timestamp shouldBe givenFistEventTimestamp

      val secondRecord = parsedRecords(1)
      secondRecord.msg shouldBe OutputRecord(givenKey, givenData * 2)
      secondRecord.timestamp shouldBe givenSecondEventTimestamp

      val thirdRecord = parsedRecords(2)
      thirdRecord.msg shouldBe OutputRecord(givenKey, givenData)
      thirdRecord.timestamp shouldBe givenThirdEventTimestamp
    }
  }

  @JsonCodec(decodeOnly = true)
  case class OutputRecord(key: String, sum: Int)

}
