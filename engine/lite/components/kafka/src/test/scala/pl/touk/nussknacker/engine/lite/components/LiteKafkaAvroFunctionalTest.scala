package pl.touk.nussknacker.engine.lite.components

import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import org.apache.avro.Schema
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.encode.ValidationMode
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{ConfluentSchemaRegistryClientFactory, MockConfluentSchemaRegistryClientFactory, MockSchemaRegistryClient}
import pl.touk.nussknacker.engine.avro.{AvroUtils, LogicalTypesGenericRecordBuilder}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.lite.util.test.SynchronousLiteInterpreter.CompilationException
import pl.touk.nussknacker.engine.lite.util.test.{KafkaConsumerRecord, LiteTestScenarioRunner}
import pl.touk.nussknacker.engine.util.namespaces.DefaultNamespacedObjectNaming

class LiteKafkaAvroFunctionalTest extends FunSuite with Matchers {

  import pl.touk.nussknacker.engine.avro.KafkaAvroBaseComponentTransformer._
  import pl.touk.nussknacker.engine.spel.Implicits._

  private val RecordSchema: Schema = AvroUtils.parseSchema(
    """{
      |  "type": "record",
      |  "namespace": "pl.touk.nussknacker.engine.avro",
      |  "name": "FullName",
      |  "fields": [
      |    { "name": "first", "type": "string" },
      |    { "name": "last", "type": "string" },
      |    { "name": "age", "type": "int" }
      |  ]
      |}
    """.stripMargin)

  private val inputTopic = "input"
  private val outputTopic = "output"

  test("should test end to end kafka avro sink / source") {
    //Given
    val scenario = ScenarioBuilder.streamingLite("check avro serialization")
      .source("my-source", "kafka-avro", TopicParamName -> s"'$inputTopic'", SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'")
      .emptySink("my-sink", "kafka-avro-raw", TopicParamName -> s"'$outputTopic'",  SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'", SinkKeyParamName -> "", SinkValueParamName -> s"#input", SinkValidationModeParameterName -> s"'${ValidationMode.strict.name}'")

    val mockRegistry = new MockSchemaRegistryClient
    val inputSchemaId = mockRegistry.registerValueSchema(inputTopic, RecordSchema)
    val outputSchemaId = mockRegistry.registerValueSchema(outputTopic, RecordSchema)

    // TODO: some helper for that
    val config = ConfigFactory.empty()
      .withValue("kafka.kafkaAddress", ConfigValueFactory.fromAnyRef("kafka:666"))
      .withValue("kafka.kafkaProperties.\"schema.registry.url\"", ConfigValueFactory.fromAnyRef("not_used"))
      // we disable default kafka components to replace them by mocked
      .withValue("components.kafka.disabled", ConfigValueFactory.fromAnyRef(true))
    val mockedKafkaComponents = new LiteKafkaComponentProvider {
      override protected def createSchemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory = new MockConfluentSchemaRegistryClientFactory(mockRegistry)
    }.create(config, ProcessObjectDependencies(config, DefaultNamespacedObjectNaming))

    val runtime = LiteTestScenarioRunner(mockedKafkaComponents, config)

    //When
    val record = new LogicalTypesGenericRecordBuilder(RecordSchema)
      .set("first", "Jan")
      .set("last", "Kowalski")
      .set("age", 18)
      .build()

    // TODO: some hele
    val input = KafkaConsumerRecord(inputTopic, ConfluentUtils.serializeRecordToBytesArray(record, inputSchemaId))
    try {
      val result = runtime.runWithData[ConsumerRecord[String, Any], ProducerRecord[String, Array[Byte]]](scenario, List(input))
        .map(r => ConfluentUtils.deserializeSchemaIdAndRecord(r.value(), RecordSchema))

      //Then
      result shouldBe List((outputSchemaId, record))
    } catch { // TODO: error handling in runWithData method signature?
      case ex: CompilationException => println(ex)
    }
  }
}
