package pl.touk.nussknacker.defaultmodel.kafkaschemaless

import com.typesafe.config.{Config, ConfigValueFactory}
import io.circe.{parser, Json, ParsingFailure}
import pl.touk.nussknacker.defaultmodel.FlinkWithKafkaSuite
import pl.touk.nussknacker.engine.api.process.TopicName.ForSource
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.ContentTypes
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.PayloadDeserializationException
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.testmode.TestProcess.ExceptionResult
import pl.touk.nussknacker.test.PatientScalaFutures

import java.nio.charset.StandardCharsets
import java.time.Instant

abstract class BaseKafkaJsonSchemalessItSpec extends FlinkWithKafkaSuite with PatientScalaFutures {

  private val jsonRecord = Json.obj(
    "first"  -> Json.fromString("Jan"),
    "middle" -> Json.fromString("Tomek"),
    "last"   -> Json.fromString("Kowalski"),
    "age"    -> Json.fromInt(30),
  )

  private val sampleString = "foobar"

  override protected def resolveModelConfig(config: Config): Config =
    super
      .resolveModelConfig(config)
      .withValue(
        s"$kafkaComponentsConfigPrefix.useDataSampleParamForSchemalessJsonTopicBasedKafkaSource",
        ConfigValueFactory.fromAnyRef(true)
      )

  def shouldRoundTripJsonMessageWithoutProvidedSchema(): Unit = {

    val inputTopic  = "input-topic-without-schema-json"
    val outputTopic = "output-topic-without-schema-json"

    kafkaClient.createTopic(inputTopic, 1)
    kafkaClient.createTopic(outputTopic, 1)
    sendAsJson(jsonRecord.toString, ForSource(inputTopic), Instant.now.toEpochMilli)

    val scenario =
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
          KafkaUniversalComponentTransformer.sinkKeyParamName.value     -> "".spel,
          KafkaUniversalComponentTransformer.sinkValueParamName.value   -> "#input".spel,
          KafkaUniversalComponentTransformer.topicParamName.value       -> s"'$outputTopic'".spel,
          KafkaUniversalComponentTransformer.contentTypeParamName.value -> s"'${ContentTypes.JSON.toString}'".spel,
        )

    testScenarioRunner.withRunningScenario(scenario) { _ =>
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
      Expression.json("null"),
      Expression.json("{}")
    )

    dataSampleExpressions.foreach { dataSampleExpression =>
      val scenario =
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
            KafkaUniversalComponentTransformer.sinkKeyParamName.value     -> "".spel,
            KafkaUniversalComponentTransformer.sinkValueParamName.value   -> "#input".spel,
            KafkaUniversalComponentTransformer.topicParamName.value       -> s"'$outputTopic'".spel,
            KafkaUniversalComponentTransformer.contentTypeParamName.value -> s"'${ContentTypes.JSON.toString}'".spel,
          )

      testScenarioRunner.withRunningScenario(scenario) { _ =>
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

    val exampleInputExpression = Expression.json(jsonRecord.toString())

    val scenario =
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
          KafkaUniversalComponentTransformer.sinkKeyParamName.value     -> "".spel,
          KafkaUniversalComponentTransformer.sinkValueParamName.value   -> "#output".spel,
          KafkaUniversalComponentTransformer.topicParamName.value       -> s"'$outputTopic'".spel,
          KafkaUniversalComponentTransformer.contentTypeParamName.value -> s"'${ContentTypes.JSON.toString}'".spel,
        )

    testScenarioRunner.withRunningScenario(scenario) { _ =>
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
      sampleString.getBytes,
      timestamp = Instant.now.toEpochMilli
    )
    val scenario =
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
          KafkaUniversalComponentTransformer.sinkKeyParamName.value     -> "".spel,
          KafkaUniversalComponentTransformer.sinkValueParamName.value   -> "#input".spel,
          KafkaUniversalComponentTransformer.topicParamName.value       -> s"'$outputTopic'".spel,
          KafkaUniversalComponentTransformer.contentTypeParamName.value -> s"'${ContentTypes.PLAIN.toString}'".spel,
        )

    testScenarioRunner.withRunningScenario(scenario) { _ =>
      val outputRecord = kafkaClient.createConsumer().consumeWithConsumerRecord(outputTopic).take(1).head

      val parsedOutput = new String(outputRecord.value(), StandardCharsets.UTF_8)

      parsedOutput shouldBe sampleString
    }
  }

  def shouldDropEventsBasedOnTheInferredDataSampleType(): Unit = {
    def assertDecodingFailureException(e: ExceptionResult[_])(exceptionMessageSuffix: String) = {
      e.nodeId shouldBe Some("start")
      e.throwable shouldBe a[PayloadDeserializationException]
      e.throwable.getMessage shouldBe s"Exception during payload deserialization: $exceptionMessageSuffix"
    }

    val dataSampleJson = Json.obj(
      "name" -> Json.fromString("Jan"),
      "age"  -> Json.fromInt(30),
    )

    val inputTopic = "input-topic-for-data-sample-json"
    kafkaClient.createTopic(inputTopic, 1)

    val scenario =
      ScenarioBuilder
        .streaming("without-schema")
        .parallelism(1)
        .source(
          "start",
          "kafka",
          KafkaUniversalComponentTransformer.topicParamName.value       -> Expression.spel(s"'$inputTopic'"),
          KafkaUniversalComponentTransformer.contentTypeParamName.value -> s"'${ContentTypes.JSON.toString}'".spel,
          KafkaUniversalComponentTransformer.dataSampleParamName.value  -> Expression.json(dataSampleJson.toString())
        )
        // We add some flink operator to enforce flink messages serialization
        .customNode("foo", "previousOutput", "previousValue", "Key" -> "''".spel, "Value" -> "''".spel)
        .emptySink("stop", "dead-end")

    testScenarioRunner.withRunningScenario(scenario) { fixture =>
      sendAsJson("[YOUR JSON]", ForSource(inputTopic), Instant.now.toEpochMilli)
      sendAsJson("\"name\"", ForSource(inputTopic), Instant.now.toEpochMilli)
      sendAsJson("123456", ForSource(inputTopic), Instant.now.toEpochMilli)

      eventually {
        val exceptions = fixture.errors
        exceptions.size should be(3)

        assertDecodingFailureException(exceptions(0))("expected json value got 'YOUR J...' (line 1, column 2)")
        assertDecodingFailureException(exceptions(1))(
          "DecodingFailure at : Got value '\"name\"' with wrong type, expecting object"
        )
        assertDecodingFailureException(exceptions(2))(
          "DecodingFailure at : Got value '123456' with wrong type, expecting object"
        )
      }
    }
  }

}
