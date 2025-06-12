package pl.touk.nussknacker.defaultmodel

import com.typesafe.config.{Config, ConfigValueFactory}
import com.typesafe.scalalogging.LazyLogging
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

class KafkaAvroSchemaWithJsonTemplateEditorItSpec
    extends FlinkWithKafkaSuite
    with PatientScalaFutures
    with LazyLogging {

  import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer

  import SampleSchemas._

  override def resolveConfig(config: Config): Config = {
    super
      .resolveConfig(config)
      .withValue("enableSingleParameterWithTemplateInsteadOfDynamicForm", ConfigValueFactory.fromAnyRef(true))
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
      validationMode: ValidationMode = ValidationMode.strict
  ) =
    ScenarioBuilder
      .streaming("todomkp-test")
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

  test("should produce a record with a default value taken from schema for kafka sink") {
    val inputTopic  = TopicName.ForSource(newTopicName("input"))
    val outputTopic = TopicName.ForSink(newTopicName("output"))

    kafkaClient.createTopic(inputTopic.name, 1)
    val outputSubject = ConfluentUtils.topicSubject(outputTopic.toUnspecialized, isKey = false)
    schemaRegistryMockClient.register(outputSubject, ConfluentUtils.convertToAvroSchema(ThirdRecordSchema))

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

  test("should produce a record with schema from json template value for kafka sink") {
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

  private def newTopicName(prefix: String) = s"$prefix-${UUID.randomUUID().toString}"

  private def consumeOneAvroMessage(topic: TopicName.ForSink) =
    valueDeserializer.deserialize(
      topic.name,
      kafkaClient.createConsumer().consumeWithConsumerRecord(topic.name).take(1).head.value()
    )

}
