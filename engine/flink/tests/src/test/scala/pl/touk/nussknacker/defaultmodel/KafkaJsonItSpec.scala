package pl.touk.nussknacker.defaultmodel

import io.circe.{Json, parser}
import org.apache.kafka.shaded.com.google.protobuf.ByteString
import pl.touk.nussknacker.engine.api.process.TopicName.ForSource
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.ContentTypes
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.time.Instant

class KafkaJsonItSpec extends FlinkWithKafkaSuite {

  private val jsonRecord = Json.obj(
    "first"  -> Json.fromString("Jan"),
    "middle" -> Json.fromString("Tomek"),
    "last"   -> Json.fromString("Kowalski")
  )

  test("should round-trip json message without provided schema") {

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

  test("should round-trip plain message without provided schema") {
    val inputTopic  = "input-topic-without-schema-plain"
    val outputTopic = "output-topic-without-schema-plain"

    kafkaClient.createTopic(inputTopic, 1)
    kafkaClient.createTopic(outputTopic, 1)
    val shortJsonInHex = "7b2261223a20357d"
    val longJsonInHex =
      "227b226669727374223a2022546f6d656b222c20226d6964646c65223a20224a616e222c20226c617374223a20224b6f77616c736b69227d22"
    val byteString = ByteString.fromHex(shortJsonInHex).toByteArray
    val big        = new BigInteger(shortJsonInHex, 16).toByteArray

    val str = new String(byteString)
    println(str)
    println(byteString.mkString("Array(", ", ", ")"))
    println(big.mkString("Array(", ", ", ")"))

    kafkaClient.sendRawMessage(inputTopic, Array.empty, byteString, timestamp = Instant.now.toEpochMilli)
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
      val outputRecord = kafkaClient
        .createConsumer()
        .consumeWithConsumerRecord(outputTopic)
        .take(1)
        .head

      outputRecord.value() shouldBe byteString
    }
  }

}
