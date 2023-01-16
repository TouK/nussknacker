package pl.touk.nussknacker.engine.lite.components

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.Schema
import org.apache.avro.generic.{GenericData, GenericDatumWriter, GenericRecordBuilder}
import org.apache.avro.io.EncoderFactory
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.Header
import org.apache.kafka.common.header.internals.{RecordHeader, RecordHeaders}
import org.apache.kafka.common.record.TimestampType
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.tags.Network
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.lite.components.LiteKafkaComponentProvider.KafkaUniversalName
import pl.touk.nussknacker.engine.lite.util.test.LiteKafkaTestScenarioRunnerBuilder
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.{SchemaVersionParamName, SinkKeyParamName, SinkRawEditorParamName, SinkValueParamName, TopicParamName}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.{AzureSchemaBasedSerdeProvider, AzureSchemaRegistryClientFactory, FullSchemaNameDecomposed}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.MockSchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.sink.UniversalKafkaSinkFactory
import pl.touk.nussknacker.engine.schemedkafka.source.UniversalKafkaSourceFactory
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.util.namespaces.DefaultNamespacedObjectNaming
import pl.touk.nussknacker.test.{KafkaConfigProperties, ValidatedValuesDetailedMessage}

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Optional

// TODO: make this test use mocked schema registry instead of the real one
@Network
class AzureSchemaRegistryKafkaAvroTest extends AnyFunSuite with Matchers with ValidatedValuesDetailedMessage {

  test("Avro using Azure Schema Registry") {
    val serdeProvider = new AzureSchemaBasedSerdeProvider
    val schemaRegistryClientFactory = new AzureSchemaRegistryClientFactory

    val eventHubsNamespace = Option(System.getenv("AZURE_EVENT_HUBS_NAMESPACE")).getOrElse("nu-cloud")
    val config = ConfigFactory.load().withFallback(ConfigFactory.empty()
      .withValue(KafkaConfigProperties.bootstrapServersProperty(), ConfigValueFactory.fromAnyRef("kafka:666"))
      .withValue(KafkaConfigProperties.property("schema.registry.url"), fromAnyRef(s"https://$eventHubsNamespace.servicebus.windows.net"))
      .withValue(KafkaConfigProperties.property("schema.group"), fromAnyRef("test-group"))
      // we disable default kafka components to replace them by mocked
      .withValue("components.kafka.disabled", ConfigValueFactory.fromAnyRef(true)))
    val testRunner = {
      val processObjectDependencies = ProcessObjectDependencies(config, DefaultNamespacedObjectNaming)
      val mockedComponents = List(
        ComponentDefinition(KafkaUniversalName, new UniversalKafkaSourceFactory(schemaRegistryClientFactory, serdeProvider, processObjectDependencies, new LiteKafkaSourceImplFactory)),
        ComponentDefinition(KafkaUniversalName, new UniversalKafkaSinkFactory(schemaRegistryClientFactory, serdeProvider, processObjectDependencies, LiteKafkaUniversalSinkImplFactory))
      )
      LiteKafkaTestScenarioRunnerBuilder(mockedComponents, List.empty, config, new MockSchemaRegistryClient).build()
    }

    val inputTopic = "input-topic"
    val outputTopic = "output-topic"
    val scenario = ScenarioBuilder.streamingLite("check json serialization")
      .source("source", KafkaUniversalName, TopicParamName -> s"'$inputTopic'", SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'")
      .emptySink("sink", KafkaUniversalName, TopicParamName -> s"'$outputTopic'", SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'",
        SinkKeyParamName -> "", SinkRawEditorParamName -> "true", SinkValueParamName -> "#input")

    def createRecordScheme(topicName: String) = {
      new AvroSchema(AvroUtils.parseSchema(
        s"""{
           |    "type": "record",
           |    "namespace": "not.important.namespace",
           |    "name": "${FullSchemaNameDecomposed.valueSchemaNameFromTopicName(topicName)}",
           |    "fields": [
           |        {
           |            "name": "a",
           |            "type": "string"
           |        }
           |    ]
           |}""".stripMargin))
    }
    val inputSchema = createRecordScheme(inputTopic)
    val outputSchema = createRecordScheme(outputTopic)

    val schemaRegistryClient = schemaRegistryClientFactory.create(KafkaConfig.parseConfig(config).schemaRegistryClientKafkaConfig)
    val inputSchemaId = schemaRegistryClient.registerSchema(inputSchema)
    val outputSchemaId = schemaRegistryClient.registerSchema(outputSchema)

    val inputValue = new GenericRecordBuilder(inputSchema.rawSchema())
      .set("a", "aValue")
      .build()
    val serializedValue = serialize(inputSchema.rawSchema(), inputValue)
    val inputHeaders = new RecordHeaders(Array[Header](new RecordHeader("content-type", s"avro/binary+$inputSchemaId".getBytes(StandardCharsets.UTF_8))))
    val inputConsumerRecord = new ConsumerRecord(inputTopic, 0, 0, 0, TimestampType.CREATE_TIME,
      0, serializedValue.length, Array[Byte](), serializedValue, inputHeaders, Optional.empty[Integer]())

    val result = testRunner.runWithRawData(scenario, List(inputConsumerRecord))
    val validResult = result.validValue
    validResult.errors shouldBe empty
    val successes = validResult.successes
    successes should have length 1
    val resultProducerRecord = successes.head
    resultProducerRecord.value() shouldEqual serializedValue
    val resultSchemaIdHeader = new String(resultProducerRecord.headers().lastHeader("content-type").value(), StandardCharsets.UTF_8).replaceFirst("avro/binary\\+", "")
    // FIXME: handle schema evolution on sink
//    resultSchemaIdHeader shouldEqual outputSchemaId
  }

  private def serialize(schema: Schema, record: GenericData.Record) = {
    val writer = new GenericDatumWriter[Any](schema, AvroUtils.genericData)
    val output = new ByteArrayOutputStream()
    val encoder = EncoderFactory.get().binaryEncoder(output, null)
    writer.write(record, encoder)
    encoder.flush()
    output.toByteArray
  }

}
