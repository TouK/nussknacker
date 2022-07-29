package pl.touk.nussknacker.engine.lite.components

import com.typesafe.config.ConfigValueFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.{RecordHeader, RecordHeaders}
import org.apache.kafka.common.record.TimestampType
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{MockConfluentSchemaRegistryClientFactory, MockSchemaRegistryClient}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.lite.util.test.LiteKafkaTestScenarioRunner
import pl.touk.nussknacker.engine.util.namespaces.DefaultNamespacedObjectNaming
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

import java.io.ByteArrayOutputStream

class UniversalSourceJsonSchemaLiteTest extends FunSuite with Matchers with ValidatedValuesDetailedMessage {

  import LiteKafkaComponentProvider._
  import LiteKafkaTestScenarioRunner._
  import io.circe.parser._
  import pl.touk.nussknacker.engine.avro.KafkaAvroBaseComponentTransformer._
  import pl.touk.nussknacker.engine.spel.Implicits._

  val schema = SchemaLoader.load(new JSONObject(
    """{
      |  "$schema": "https://json-schema.org/draft-07/schema",
      |  "type": "object",
      |  "properties": {
      |    "first": {
      |      "type": "string"
      |    },
      |    "last": {
      |      "type": "string"
      |    },
      |    "age": {
      |      "type": "integer"
      |    }
      |  }
      |}""".stripMargin))

  private val inputTopic = "input"
  private val outputTopic = "output"

  private val scenario = ScenarioBuilder.streamingLite("check json serialization")
    .source("my-source", KafkaUniversalName, TopicParamName -> s"'$inputTopic'", SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'")
    .emptySink("my-sink", KafkaUniversalName, TopicParamName -> s"'$outputTopic'", SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'", SinkKeyParamName -> "",
      "first" -> s"#input.first", "last" -> "#input.last", "age" -> "#input.age")


  test("should read data on json schema based universal source when schemaId in header") {
    //Given
    val runtime = createRuntime
    val schemaId = runtime.registerJsonSchema(inputTopic, schema)
    runtime.registerJsonSchema(outputTopic, schema)

    //When
    val record =
      """{
        |  "first": "John",
        |  "last": "Doe",
        |  "age": 21
        |}""".stripMargin.getBytes()

    val headers = new RecordHeaders().add(new RecordHeader("value.schemaId", s"$schemaId".getBytes()))
    val input = new ConsumerRecord(inputTopic, 1, 1, ConsumerRecord.NO_TIMESTAMP, TimestampType.NO_TIMESTAMP_TYPE, ConsumerRecord.NULL_CHECKSUM, ConsumerRecord.NULL_SIZE, ConsumerRecord.NULL_SIZE, null.asInstanceOf[Array[Byte]], record, headers)

    val list: List[ConsumerRecord[Array[Byte], Array[Byte]]] = List(input)
    val result = runtime.runWithRawData(scenario, list).validValue

    //Then
    parse(new String(input.value())) shouldBe parse(new String(result.successes.head.value()))
  }

  test("should read data on json schema based universal source when schemaId in wire-format") {
    //Given
    val runtime = createRuntime
    val schemaId = runtime.registerJsonSchema(inputTopic, schema)
    runtime.registerJsonSchema(outputTopic, schema)

    //When
    val stringRecord =
      """{
        |  "first": "John",
        |  "last": "Doe",
        |  "age": 21
        |}""".stripMargin
    val record = stringRecord.getBytes()

    val recordWithWireFormatSchemaId = new ByteArrayOutputStream
    ConfluentUtils.writeSchemaId(schemaId, recordWithWireFormatSchemaId)
    recordWithWireFormatSchemaId.write(record)

    val input = new ConsumerRecord(inputTopic, 1, 1, null.asInstanceOf[Array[Byte]], recordWithWireFormatSchemaId.toByteArray)

    val list: List[ConsumerRecord[Array[Byte], Array[Byte]]] = List(input)
    val result = runtime.runWithRawData(scenario, list).validValue

    //Then
    parse(stringRecord) shouldBe parse(new String(result.successes.head.value()))
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
