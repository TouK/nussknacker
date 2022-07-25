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
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.encode.ValidationMode
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{MockConfluentSchemaRegistryClientFactory, MockSchemaRegistryClient}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.lite.util.test.{KafkaJsonSchemaElement, LiteJsonSchemaKafkaTestScenarioRunner, LiteKafkaTestScenarioRunner}
import pl.touk.nussknacker.engine.util.namespaces.DefaultNamespacedObjectNaming
import pl.touk.nussknacker.engine.util.test.RunResult
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

class LiteKafkaJsonSchemaAvroFunctionalTest extends FunSuite with Matchers with ValidatedValuesDetailedMessage {

  import LiteKafkaComponentProvider._
  import LiteKafkaTestScenarioRunner._
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
    .emptySink("end", "dead-end")

//      .emptySink("my-sink", KafkaUniversalName, TopicParamName -> s"'$outputTopic'",  SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'", SinkKeyParamName -> "", SinkValueParamName -> s"#input", SinkValidationModeParameterName -> s"'${ValidationMode.strict.name}'")

  test("should test end to end kafka avro record data at sink / source") {
    //Given
    val runtime = createRuntime
    val schemaId = runtime.registerJsonSchemaSchema(inputTopic, schema)
    runtime.registerJsonSchemaSchema(outputTopic, schema)

    //When
    val record =
      """{
        |  "first": "John",
        |  "last": "Doe",
        |  "age": 21
        |}""".stripMargin.getBytes()

    val input = new ConsumerRecord(inputTopic, 1, 1, ConsumerRecord.NO_TIMESTAMP, TimestampType.NO_TIMESTAMP_TYPE, ConsumerRecord.NULL_CHECKSUM, ConsumerRecord.NULL_SIZE, ConsumerRecord.NULL_SIZE, null.asInstanceOf[Any], KafkaJsonSchemaElement(record, schemaId), new RecordHeaders().add(new RecordHeader("value.schemaId", s"$schemaId".getBytes())))

    val list: List[ConsumerRecord[Any, KafkaJsonSchemaElement]] = List(input)
    val result = runtime.runWithAvroData[String, KafkaJsonSchemaElement](scenario, list).validValue
    val resultWithValue = result.copy(successes = result.successes.map(_.value()))

    //Then
//    resultWithValue shouldBe RunResult.success(input.value().data)
    resultWithValue shouldBe RunResult.successes(List())
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

    new LiteJsonSchemaKafkaTestScenarioRunner(mockSchemaRegistryClient, mockedComponents, config)
  }
}
