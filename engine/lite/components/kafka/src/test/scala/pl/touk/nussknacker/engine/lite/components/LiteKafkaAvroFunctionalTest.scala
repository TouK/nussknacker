package pl.touk.nussknacker.engine.lite.components

import com.typesafe.config.ConfigValueFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.encode.ValidationMode
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{MockConfluentSchemaRegistryClientFactory, MockSchemaRegistryClient}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.lite.util.test.{KafkaAvroConsumerRecord, LiteKafkaTestScenarioRunner}
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder
import pl.touk.nussknacker.engine.util.namespaces.DefaultNamespacedObjectNaming
import pl.touk.nussknacker.engine.util.test.RunResult
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

class LiteKafkaAvroFunctionalTest extends FunSuite with Matchers with ValidatedValuesDetailedMessage {

  import LiteKafkaComponentProvider._
  import LiteKafkaTestScenarioRunner._
  import pl.touk.nussknacker.engine.avro.KafkaAvroBaseComponentTransformer._
  import pl.touk.nussknacker.engine.spel.Implicits._

  private val recordSchema = AvroUtils.parseSchema(
    """{
      |  "type": "record",
      |  "namespace": "pl.touk.nussknacker.engine.avro",
      |  "name": "FullName",
      |  "fields": [
      |    { "name": "first", "type": "string" },
      |    { "name": "last", "type": "string" },
      |    { "name": "age", "type": "int" },
      |    { "name": "sex", "type": {"type": "enum", "name": "Sex", "symbols": ["MALE", "FEMALE"]}},
      |    { "name": "address", "type": {"type":"record", "name": "Address", "fields": [{ "name": "city", "type": "string" }]} }
      |  ]
      |}
    """.stripMargin)

  private val primitiveSchema = AvroUtils.parseSchema("""{"type": "string"}""")

  private val inputTopic = "input"
  private val outputTopic = "output"

  private val simpleAvroScenario = ScenarioBuilder.streamingLite("check avro serialization")
    .source("my-source", KafkaAvroName, TopicParamName -> s"'$inputTopic'", SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'")
    .emptySink("my-sink", KafkaAvroName, TopicParamName -> s"'$outputTopic'",  SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'", SinkKeyParamName -> "",
        "first" -> "#input.first",
        "last" -> "#input.last",
        "age" -> "#input.age",
        "sex" -> "#input.sex",
        "address.city" -> "'Warsaw'"
    )

  test("should test end to end kafka avro record data at sink / source") {
    //Given
    val runtime = createRuntime
    val schemaId = runtime.registerAvroSchema(outputTopic, recordSchema)
    runtime.registerJsonSchema(inputTopic,
      """
        |{
        |  "type": "object",
        |  "required": [
        |    "first",
        |    "last",
        |    "age",
        |    "sex",
        |    "address"
        |  ],
        |  "additionalProperties": false,
        |  "properties": {
        |    "first": {
        |      "type": "string"
        |    },
        |    "last": {
        |      "type": "string"
        |    },
        |    "age": {
        |      "type": "integer",
        |      "minimum": -2147483648,
        |      "maximum": 2147483647
        |    },
        |    "sex": {
        |      "type": "string"
        |    }
        |  },
        |  "definitions": {
        |  }
        |}
        |""".stripMargin)

    //When
    val record = AvroUtils.createRecord(recordSchema, Map(
      "first" -> "Jan", "last" -> "Kowalski", "age" -> 18, "sex" -> "MALE", "address" -> Map("city" -> "Warsaw")
    ))

    val jsonRecord = BestEffortJsonEncoder.defaultForTests.encode( Map(
      "first" -> "Jan", "last" -> "Kowalski", "age" -> 18, "sex" -> "MALE"
    )).noSpaces.getBytes

    val input = KafkaAvroConsumerRecord(inputTopic, record, schemaId)
    val x: GenericRecord = input.value().data
    val result: RunResult[ProducerRecord[String, GenericRecord]] = runtime.runWithRowDataAndToAvro[String, GenericRecord](simpleAvroScenario, List(input).map(d => new ConsumerRecord[Array[Byte], Array[Byte]](inputTopic, 0, 0, null, jsonRecord))
    ).validValue

    val resultWithValue: RunResult[GenericRecord] = result.copy(successes = result.successes.map(_.value()))

    //Then
    resultWithValue shouldBe RunResult.success(input.value().data)
  }

  test("should test end to end kafka avro primitive data at sink / source") {
    //Given
    val runtime = createRuntime
    val schemaId = runtime.registerAvroSchema(inputTopic, primitiveSchema)
    runtime.registerAvroSchema(outputTopic, primitiveSchema)

    //When
    val record = "lcl"

    val input = KafkaAvroConsumerRecord(inputTopic, record, schemaId)
    val result = runtime.runWithAvroData(simpleAvroScenario, List(input)).validValue
    val resultWithValue = result.copy(successes = result.successes.map(_.value()))

    //Then
    resultWithValue shouldBe RunResult.success(input.value().data)
  }

  private def createRuntime = {
    val config = DefaultKafkaConfig
      // we disable default kafka components to replace them by mocked
      .withValue("components.kafka.disabled", ConfigValueFactory.fromAnyRef(true))
      .withValue("kafka.kafkaProperties.\"schema.registry.url\"", fromAnyRef("schema-registry:666"))

    val mockSchemaRegistryClient = new MockSchemaRegistryClient
    val mockedKafkaComponents = new LiteKafkaComponentProvider(new MockConfluentSchemaRegistryClientFactory(mockSchemaRegistryClient))
    val processObjectDependencies = ProcessObjectDependencies(config, DefaultNamespacedObjectNaming)
    val mockedComponents = mockedKafkaComponents.create(config, processObjectDependencies)

    new LiteKafkaTestScenarioRunner(mockSchemaRegistryClient, mockedComponents, config)
  }
}
