package pl.touk.nussknacker.defaultmodel

import com.typesafe.config.{Config, ConfigValueFactory}
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.json.JsonSchema
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName.ToUnspecializedTopicName
import pl.touk.nussknacker.engine.schemedkafka._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.test.PatientScalaFutures

import java.util.UUID

class KafkaSinkWithJsonTemplateEditorItSpec extends FlinkWithKafkaSuite with PatientScalaFutures with LazyLogging {

  import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer

  import SampleSchemas._

  override def resolveModelConfig(config: Config): Config = {
    super
      .resolveModelConfig(config)
      .withValue("jsonLikeValuesEnteringMode", ConfigValueFactory.fromAnyRef("SingleJsonTemplateParameter"))
  }

  private def scenarioWithDefaultSinkValue(
      inputTopic: String,
      outputTopic: String,
      validationMode: ValidationMode = ValidationMode.strict
  ) =
    ScenarioBuilder
      .streaming("kafka-test")
      .parallelism(1)
      .source(
        "start",
        "kafka",
        KafkaUniversalComponentTransformer.topicParamName.value       -> s"'$inputTopic'".spel,
        KafkaUniversalComponentTransformer.contentTypeParamName.value -> "'JSON'".spel
      )
      .emptySink(
        "end",
        "kafka",
        KafkaUniversalComponentTransformer.topicParamName.value -> s"'$outputTopic'".spel,
        KafkaUniversalComponentTransformer.schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel,
        KafkaUniversalComponentTransformer.sinkKeyParamName.value            -> "".spel,
        KafkaUniversalComponentTransformer.sinkValidationModeParamName.value -> s"'${validationMode.name}'".spel
      )

  private def scenario(
      inputTopic: String,
      outputTopic: String,
      sinkValue: Expression,
      validationMode: ValidationMode
  ) =
    ScenarioBuilder
      .streaming("test-scenario")
      .parallelism(1)
      .source(
        "start",
        "kafka",
        KafkaUniversalComponentTransformer.topicParamName.value       -> s"'$inputTopic'".spel,
        KafkaUniversalComponentTransformer.contentTypeParamName.value -> "'JSON'".spel
      )
      .emptySink(
        "end",
        "kafka",
        KafkaUniversalComponentTransformer.topicParamName.value -> s"'$outputTopic'".spel,
        KafkaUniversalComponentTransformer.schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel,
        KafkaUniversalComponentTransformer.sinkKeyParamName.value            -> "".spel,
        KafkaUniversalComponentTransformer.sinkValidationModeParamName.value -> s"'${validationMode.name}'".spel,
        KafkaUniversalComponentTransformer.sinkValueParamName.value          -> sinkValue,
      )

  test("should produce an Avro record using default values defined in the Avro schema for the Kafka sink") {
    val inputTopic  = TopicName.ForSource(newTopicName("input"))
    val outputTopic = TopicName.ForSink(newTopicName("output"))

    kafkaClient.createTopic(inputTopic.name, 1)
    val outputSubject = ConfluentUtils.topicSubject(outputTopic.toUnspecialized, isKey = false)
    schemaRegistryMockClient.register(outputSubject, ConfluentUtils.convertToAvroSchema(ThirdRecordSchema, Some(1)))

    sendAsJson("""{ "any": "schema" }""", inputTopic).futureValue

    run(
      scenarioWithDefaultSinkValue(
        inputTopic = inputTopic.name,
        outputTopic = outputTopic.name,
        validationMode = ValidationMode.strict
      )
    ) {
      val expectedMessage = avroEncoder.encodeOrError(
        Map(
          "first"     -> "Jan",
          "middle"    -> null,
          "last"      -> "Kowalski",
          "age"       -> 18,
          "height"    -> 1.80f,
          "weight"    -> 70.5d,
          "lastLogin" -> 0
        ),
        ThirdRecordSchema
      )
      val processed = consumeOneAvroMessage(outputTopic)
      processed shouldEqual expectedMessage
    }
  }

  test("should produce an Avro record with value taken from json template for kafka sink") {
    val inputTopic  = TopicName.ForSource(newTopicName("input"))
    val outputTopic = TopicName.ForSink(newTopicName("output"))

    kafkaClient.createTopic(inputTopic.name, partitions = 1)

    val outputSubject = ConfluentUtils.topicSubject(outputTopic.toUnspecialized, isKey = false)
    schemaRegistryMockClient.register(outputSubject, ConfluentUtils.convertToAvroSchema(ThirdRecordSchema))

    val message =
      s"""
         |{
         |  "first": "Jan",
         |  "middle": "Tomek",
         |  "last": "Kowalski"
         |}
         |""".stripMargin
    sendAsJson(message, inputTopic).futureValue

    val sinkValue =
      Expression.jsonTemplate {
        s"""
           |{
           |  "first": "#{ #input["first"] }",
           |  "middle": "#{ #input["middle"] }",
           |  "last": "#{ #input["last"] }",
           |  "age": 50,
           |  "height": 2.01,
           |  "weight": 110.11,
           |  "lastLogin": 1234567890
           |}
           |""".stripMargin
      }

    run(
      scenario(
        inputTopic = inputTopic.name,
        outputTopic = outputTopic.name,
        sinkValue = sinkValue,
        validationMode = ValidationMode.strict
      )
    ) {
      val expectedMessage = avroEncoder.encodeOrError(
        Map(
          "first"     -> "Jan",
          "middle"    -> "Tomek",
          "last"      -> "Kowalski",
          "age"       -> 50,
          "height"    -> 2.01f,
          "weight"    -> 110.11d,
          "lastLogin" -> 1234567890
        ),
        ThirdRecordSchema
      )
      val processed = consumeOneAvroMessage(outputTopic)
      processed shouldEqual expectedMessage
    }
  }

  test("should produce a JSON record with a default value taken from JSON schema for kafka sink") {
    val inputTopic  = TopicName.ForSource(newTopicName("input"))
    val outputTopic = TopicName.ForSink(newTopicName("output"))

    kafkaClient.createTopic(inputTopic.name, 1)
    val outputSubject = ConfluentUtils.topicSubject(outputTopic.toUnspecialized, isKey = false)
    schemaRegistryMockClient.register(outputSubject, jsonSchema)

    sendAsJson("""{ "any": "schema" }""", inputTopic).futureValue

    run(
      scenarioWithDefaultSinkValue(
        inputTopic = inputTopic.name,
        outputTopic = outputTopic.name,
        validationMode = ValidationMode.strict
      )
    ) {
      val expectedMessage =
        io.circe.parser
          .parse(
            s"""
               |{
               |  "first": "Jan",
               |  "middle": null,
               |  "last": "Kowalski",
               |  "age": 18,
               |  "height": 1.80,
               |  "weight": 70.5,
               |  "lastLogin": 0
               |}
               |""".stripMargin
          )
          .toOption
          .get

      val rawMessage = new String(consumeOneMessage(outputTopic))
      val processed  = io.circe.parser.parse(rawMessage)
      processed shouldEqual Right(expectedMessage)
    }
  }

  test("should produce a JSON record with value taken from json template for kafka sink") {
    val inputTopic  = TopicName.ForSource(newTopicName("input"))
    val outputTopic = TopicName.ForSink(newTopicName("output"))

    kafkaClient.createTopic(inputTopic.name, partitions = 1)

    val outputSubject = ConfluentUtils.topicSubject(outputTopic.toUnspecialized, isKey = false)
    schemaRegistryMockClient.register(outputSubject, jsonSchema)

    val message =
      s"""
         |{
         |  "first": "Jan",
         |  "middle": "Tomek",
         |  "last": "Kowalski"
         |}
         |""".stripMargin
    sendAsJson(message, inputTopic).futureValue

    val sinkValue =
      Expression.jsonTemplate {
        s"""
           |{
           |  "first": "#{ #input["first"] }",
           |  "middle": "#{ #input["middle"] }",
           |  "last": "#{ #input["last"] }",
           |  "age": 50,
           |  "height": 2.01,
           |  "weight": 110.11,
           |  "lastLogin": 1234567890
           |}
           |""".stripMargin
      }

    run(
      scenario(
        inputTopic = inputTopic.name,
        outputTopic = outputTopic.name,
        sinkValue = sinkValue,
        validationMode = ValidationMode.strict
      )
    ) {
      val expectedMessage =
        io.circe.parser
          .parse(
            s"""
             |{
             |  "first": "Jan",
             |  "middle": "Tomek",
             |  "last": "Kowalski",
             |  "age": 50,
             |  "height": 2.01,
             |  "weight": 110.11,
             |  "lastLogin": 1234567890
             |}
             |""".stripMargin
          )
          .toOption
          .get

      val rawMessage = new String(consumeOneMessage(outputTopic))
      val processed  = io.circe.parser.parse(rawMessage)
      processed shouldEqual Right(expectedMessage)
    }
  }

  private def newTopicName(prefix: String) = s"$prefix-${UUID.randomUUID().toString}"

  private def consumeOneAvroMessage(topic: TopicName.ForSink) =
    valueDeserializer.deserialize(
      topic.name,
      consumeOneMessage(topic)
    )

  private def consumeOneMessage(topic: TopicName.ForSink) = {
    kafkaClient.createConsumer().consumeWithConsumerRecord(topic.name).take(1).head.value()
  }

  private val jsonSchema = new JsonSchema(
    s"""
       |{
       |  "$$schema": "http://json-schema.org/draft-07/schema#",
       |  "title": "UserProfile",
       |  "type": "object",
       |  "properties": {
       |    "first": {
       |      "type": "string",
       |      "default": "Jan"
       |    },
       |    "middle": {
       |      "type": ["string", "null"],
       |      "default": null
       |    },
       |    "last": {
       |      "type": "string",
       |      "default": "Kowalski"
       |    },
       |    "age": {
       |      "type": "integer",
       |      "default": 18
       |    },
       |    "height": {
       |      "type": "number",
       |      "default": 1.8
       |    },
       |    "weight": {
       |      "type": "number",
       |      "default": 70.5
       |    },
       |    "lastLogin": {
       |      "type": "integer"
       |    }
       |  },
       |  "required": ["first", "middle", "last", "age", "height", "weight", "lastLogin"]
       |}
       |""".stripMargin
  )

}
