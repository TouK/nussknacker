package pl.touk.nussknacker.defaultmodel.kafkaschemaless

import io.circe.{Json, parser}
import pl.touk.nussknacker.defaultmodel.FlinkWithKafkaSuite
import pl.touk.nussknacker.engine.api.process.TopicName.ForSource
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.ContentTypes
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion

import java.nio.charset.StandardCharsets
import java.time.Instant

abstract class BaseKafkaJsonSchemalessItSpec extends FlinkWithKafkaSuite {

  private val jsonRecord = Json.obj(
    "first"  -> Json.fromString("Jan"),
    "middle" -> Json.fromString("Tomek"),
    "last"   -> Json.fromString("Kowalski")
  )

  def shouldRoundTripJsonMessageWithoutProvidedSchema(): Unit = {

    val inputTopic  = "input-topic-without-schema-json"
    val outputTopic = "output-topic-without-schema-json"

    kafkaClient.createTopic(inputTopic, 1)
    kafkaClient.createTopic(outputTopic, 1)
    sendAsJson(jsonRecord.toString, ForSource(inputTopic), Instant.now.toEpochMilli)

    val process =
      ScenarioBuilder
        .streaming("without-schema")
        .parallelism(1)
        .source(
          "start",
          "kafka",
          KafkaUniversalComponentTransformer.topicParamName.value       -> Expression.spel(s"'$inputTopic'"),
          KafkaUniversalComponentTransformer.contentTypeParamName.value -> s"'${ContentTypes.JSON.toString}'".spel
        )
        .emptySink(
          "end",
          "kafka",
          KafkaUniversalComponentTransformer.sinkKeyParamName.value       -> "".spel,
          KafkaUniversalComponentTransformer.sinkRawEditorParamName.value -> "true".spel,
          KafkaUniversalComponentTransformer.sinkValueParamName.value     -> "#input".spel,
          KafkaUniversalComponentTransformer.topicParamName.value         -> s"'$outputTopic'".spel,
          KafkaUniversalComponentTransformer.contentTypeParamName.value   -> s"'${ContentTypes.JSON.toString}'".spel,
          KafkaUniversalComponentTransformer.sinkValidationModeParamName.value -> s"'${ValidationMode.lax.name}'".spel
        )

    run(process) {
      val outputRecord = kafkaClient.createConsumer().consumeWithConsumerRecord(outputTopic).take(1).head
      val parsedOutput = parser
        .parse(new String(outputRecord.value(), StandardCharsets.UTF_8))
        .fold(throw _, identity)

      parsedOutput shouldBe jsonRecord
    }
  }

  def shouldRoundTripPlainMessageWithoutProvidedSchema() {
    val inputTopic  = "input-topic-without-schema-plain"
    val outputTopic = "output-topic-without-schema-plain"

    kafkaClient.createTopic(inputTopic, 1)
    kafkaClient.createTopic(outputTopic, 1)
    kafkaClient.sendRawMessage(
      inputTopic,
      Array.empty,
      jsonRecord.toString().getBytes,
      timestamp = Instant.now.toEpochMilli
    )
    val process =
      ScenarioBuilder
        .streaming("without-schema")
        .parallelism(1)
        .source(
          "start",
          "kafka",
          KafkaUniversalComponentTransformer.topicParamName.value       -> Expression.spel(s"'$inputTopic'"),
          KafkaUniversalComponentTransformer.contentTypeParamName.value -> s"'${ContentTypes.PLAIN.toString}'".spel
        )
        .emptySink(
          "end",
          "kafka",
          KafkaUniversalComponentTransformer.sinkKeyParamName.value       -> "".spel,
          KafkaUniversalComponentTransformer.sinkRawEditorParamName.value -> "true".spel,
          KafkaUniversalComponentTransformer.sinkValueParamName.value     -> "#input".spel,
          KafkaUniversalComponentTransformer.topicParamName.value         -> s"'$outputTopic'".spel,
          KafkaUniversalComponentTransformer.contentTypeParamName.value   -> s"'${ContentTypes.PLAIN.toString}'".spel,
          KafkaUniversalComponentTransformer.sinkValidationModeParamName.value -> s"'${ValidationMode.lax.name}'".spel
        )

    run(process) {
      val outputRecord = kafkaClient.createConsumer().consumeWithConsumerRecord(outputTopic).take(1).head

      val parsedOutput = parser
        .parse(new String(outputRecord.value(), StandardCharsets.UTF_8))
        .fold(throw _, identity)

      parsedOutput shouldBe jsonRecord
    }
  }

}
