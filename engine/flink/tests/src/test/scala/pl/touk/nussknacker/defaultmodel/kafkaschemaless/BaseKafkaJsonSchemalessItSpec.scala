package pl.touk.nussknacker.defaultmodel.kafkaschemaless

import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.{Invalid, Valid}
import io.circe.{parser, Json}
import pl.touk.nussknacker.defaultmodel.FlinkWithKafkaSuite
import pl.touk.nussknacker.engine.api.{NodeId, Params}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  OutputVariableNameValue,
  TypedNodeDependencyValue
}
import pl.touk.nussknacker.engine.api.process.{Source, SourceFactory, TestDataGenerator, TopicName}
import pl.touk.nussknacker.engine.api.process.TopicName.ForSource
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceTestSupport
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
    "last"   -> Json.fromString("Kowalski"),
    "age"    -> Json.fromInt(30),
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

  def shouldRoundTripJsonMessageWithoutSchemaDerivedFromProvidedDataSample(): Unit = {

    val inputTopic  = "input-topic-without-derived-schema-json"
    val outputTopic = "output-topic-without-derived-schema-json"

    kafkaClient.createTopic(inputTopic, 1)
    kafkaClient.createTopic(outputTopic, 1)
    sendAsJson(jsonRecord.toString, ForSource(inputTopic), Instant.now.toEpochMilli)

    val dataSampleExpressions = List(
      "null".spel,
      "'{}'".spel
    )

    dataSampleExpressions.foreach { dataSampleExpression =>
      val process =
        ScenarioBuilder
          .streaming("without-schema")
          .parallelism(1)
          .source(
            "start",
            "kafka",
            KafkaUniversalComponentTransformer.topicParamName.value       -> Expression.spel(s"'$inputTopic'"),
            KafkaUniversalComponentTransformer.contentTypeParamName.value -> s"'${ContentTypes.JSON.toString}'".spel,
            KafkaUniversalComponentTransformer.dataSampleParamName.value  -> dataSampleExpression
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

  }

  def shouldRoundTripJsonMessageWithSchemaDerivedFromProvidedDataSample(): Unit = {

    val inputTopic  = "input-topic-with-derived-schema-json"
    val outputTopic = "output-topic-with-derived-schema-json"

    kafkaClient.createTopic(inputTopic, 1)
    kafkaClient.createTopic(outputTopic, 1)
    sendAsJson(jsonRecord.toString, ForSource(inputTopic), Instant.now.toEpochMilli)

    val exampleInputExpression = s"""'${jsonRecord.toString()}'""".spel

    val process =
      ScenarioBuilder
        .streaming("without-schema")
        .parallelism(1)
        .source(
          "start",
          "kafka",
          KafkaUniversalComponentTransformer.topicParamName.value       -> Expression.spel(s"'$inputTopic'"),
          KafkaUniversalComponentTransformer.contentTypeParamName.value -> s"'${ContentTypes.JSON.toString}'".spel,
          KafkaUniversalComponentTransformer.dataSampleParamName.value  -> exampleInputExpression
        )
        .buildVariable(
          "variable",
          "variable",
          "firstName"  -> Expression.spel("#input.first.toUpperCase"),
          "middleName" -> Expression.spel("#input.middle.toUpperCase"),
          "lastName"   -> Expression.spel("#input.last.toUpperCase"),
          "isAdult"    -> Expression.spel("#input.age >= 18")
        )
        .buildVariable(
          "output",
          "output",
          "description" -> Expression.spel(
            "#variable.firstName + ' ' + #variable.middleName + ' ' + #variable.lastName.toUpperCase +' is ' + #input.age + ' years old'"
          )
        )
        .emptySink(
          "end",
          "kafka",
          KafkaUniversalComponentTransformer.sinkKeyParamName.value       -> "".spel,
          KafkaUniversalComponentTransformer.sinkRawEditorParamName.value -> "true".spel,
          KafkaUniversalComponentTransformer.sinkValueParamName.value     -> "#output".spel,
          KafkaUniversalComponentTransformer.topicParamName.value         -> s"'$outputTopic'".spel,
          KafkaUniversalComponentTransformer.contentTypeParamName.value   -> s"'${ContentTypes.JSON.toString}'".spel,
          KafkaUniversalComponentTransformer.sinkValidationModeParamName.value -> s"'${ValidationMode.lax.name}'".spel
        )

    run(process) {
      val outputRecord = kafkaClient.createConsumer().consumeWithConsumerRecord(outputTopic).take(1).head
      val parsedOutput = parser
        .parse(new String(outputRecord.value(), StandardCharsets.UTF_8))
        .fold(throw _, identity)

      val expectedOutput =
        io.circe.parser
          .parse(
            """
            |{
            |  "description": "JAN TOMEK KOWALSKI is 30 years old"
            |}
            |""".stripMargin
          )
          .toOption
          .get

      parsedOutput shouldBe expectedOutput

    }
  }

  def shouldRoundTripPlainMessageWithoutProvidedSchema(): Unit = {
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
