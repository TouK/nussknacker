package pl.touk.nussknacker.engine.lite.components

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.generic.{GenericRecord, GenericRecordBuilder}
import org.apache.avro.{Schema, SchemaBuilder}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.Header
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.tags.Network
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.lite.components.LiteKafkaComponentProvider.KafkaUniversalName
import pl.touk.nussknacker.engine.lite.components.utils.AvroTestData
import pl.touk.nussknacker.engine.lite.util.test.LiteKafkaTestScenarioRunnerBuilder
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.{SchemaVersionParamName, SinkKeyParamName, SinkRawEditorParamName, SinkValueParamName, TopicParamName}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.{AzureSchemaBasedSerdeProvider, AzureSchemaRegistryClientFactory, AzureUtils, SchemaNameTopicMatchStrategy}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.MockSchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaId, SchemaVersionOption}
import pl.touk.nussknacker.engine.schemedkafka.sink.UniversalKafkaSinkFactory
import pl.touk.nussknacker.engine.schemedkafka.source.UniversalKafkaSourceFactory
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.util.namespaces.DefaultNamespacedObjectNaming
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.RunnerListResult
import pl.touk.nussknacker.test.{KafkaConfigProperties, ValidatedValuesDetailedMessage}

import java.util.Optional

// TODO: make this test use mocked schema registry instead of the real one
@Network
class AzureSchemaRegistryKafkaAvroTest extends AnyFunSuite with Matchers with ValidatedValuesDetailedMessage {

  private val serdeProvider = new AzureSchemaBasedSerdeProvider
  private val schemaRegistryClientFactory = new AzureSchemaRegistryClientFactory

  private val eventHubsNamespace = Option(System.getenv("AZURE_EVENT_HUBS_NAMESPACE")).getOrElse("nu-cloud")
  private val config = ConfigFactory.load().withFallback(ConfigFactory.empty()
    .withValue(KafkaConfigProperties.bootstrapServersProperty(), ConfigValueFactory.fromAnyRef("kafka:666"))
    .withValue(KafkaConfigProperties.property("schema.registry.url"), fromAnyRef(s"https://$eventHubsNamespace.servicebus.windows.net"))
    .withValue(KafkaConfigProperties.property("schema.group"), fromAnyRef("test-group"))
    // we disable default kafka components to replace them by mocked
    .withValue("components.kafka.disabled", ConfigValueFactory.fromAnyRef(true)))

  private val testRunner = {
    val processObjectDependencies = ProcessObjectDependencies(config, DefaultNamespacedObjectNaming)
    val mockedComponents = List(
      ComponentDefinition(KafkaUniversalName, new UniversalKafkaSourceFactory(schemaRegistryClientFactory, serdeProvider, processObjectDependencies, new LiteKafkaSourceImplFactory)),
      ComponentDefinition(KafkaUniversalName, new UniversalKafkaSinkFactory(schemaRegistryClientFactory, serdeProvider, processObjectDependencies, LiteKafkaUniversalSinkImplFactory))
    )
    LiteKafkaTestScenarioRunnerBuilder(mockedComponents, List.empty, config, new MockSchemaRegistryClient).build()
  }

  private val schemaRegistryClient = schemaRegistryClientFactory.create(KafkaConfig.parseConfig(config).schemaRegistryClientKafkaConfig)

  test("round-trip Avro serialization using Azure Schema Registry") {
    val scenarioName = "avro"
    val inputTopic = s"$scenarioName-input"
    val outputTopic = s"$scenarioName-output"

    val aFieldOnly = (assembler: SchemaBuilder.FieldAssembler[Schema]) =>
      assembler.name("a").`type`(AvroTestData.stringSchema).noDefault()
    val inputSchema = createRecordSchema(inputTopic, aFieldOnly)
    val outputSchema = createRecordSchema(outputTopic, aFieldOnly)

    val inputSchemaId = SchemaId.fromString(schemaRegistryClient.registerSchemaVersionIfNotExists(inputSchema).getId)
    val outputSchemaId = SchemaId.fromString(schemaRegistryClient.registerSchemaVersionIfNotExists(outputSchema).getId)

    val scenario = ScenarioBuilder.streamingLite(scenarioName)
      .source("source", KafkaUniversalName, TopicParamName -> s"'$inputTopic'", SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'")
      .emptySink("sink", KafkaUniversalName, TopicParamName -> s"'$outputTopic'", SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'",
        SinkKeyParamName -> "", SinkRawEditorParamName -> "true", SinkValueParamName -> "#input")

    val inputValue = new GenericRecordBuilder(inputSchema.rawSchema())
      .set("a", "aValue")
      .build()
    val inputConsumerRecord = wrapWithConsumerRecord(inputTopic, inputSchemaId, AvroUtils.serializeContainerToBytesArray(inputValue))

    val result = testRunner.runWithRawData(scenario, List(inputConsumerRecord))
    val (resultProducerRecord, resultSchemaIdHeader) = verifyOneSuccessRecord(result)
    resultProducerRecord.value() shouldEqual inputConsumerRecord.value()
    resultSchemaIdHeader shouldEqual outputSchemaId
  }

  test("schema evolution in Avro source using Azure Schema Registry") {
    val scenarioName = "avro-schemaevolution"
    val inputTopic = s"$scenarioName-input"
    val outputTopic = s"$scenarioName-output"

    val aFieldOnly = (assembler: SchemaBuilder.FieldAssembler[Schema]) =>
      assembler.name("a").`type`(AvroTestData.stringSchema).noDefault()
    val bDefaultValue = "bDefault"
    val abFields = (assembler: SchemaBuilder.FieldAssembler[Schema]) =>
      assembler
        .name("a").`type`(AvroTestData.stringSchema).noDefault()
        .name("b").`type`(AvroTestData.stringSchema).withDefault(bDefaultValue)
    val inputSchemaV1 = createRecordSchema(inputTopic, aFieldOnly)
    val inputSchemaV2 = createRecordSchema(inputTopic, abFields)
    val outputSchema = createRecordSchema(outputTopic, abFields)

    val inputSchemaV1Props = schemaRegistryClient.registerSchemaVersionIfNotExists(inputSchemaV1)
    val inputSchemaV2Props = schemaRegistryClient.registerSchemaVersionIfNotExists(inputSchemaV2)
    val outputSchemaId = SchemaId.fromString(schemaRegistryClient.registerSchemaVersionIfNotExists(outputSchema).getId)

    val scenario = ScenarioBuilder.streamingLite(scenarioName)
      .source("source", KafkaUniversalName, TopicParamName -> s"'$inputTopic'", SchemaVersionParamName -> s"'${inputSchemaV2Props.getVersion}'")
      .filter("filter-b-default", s"#input.b == '$bDefaultValue'")
      .emptySink("sink", KafkaUniversalName, TopicParamName -> s"'$outputTopic'", SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'",
        SinkKeyParamName -> "", SinkRawEditorParamName -> "true", SinkValueParamName -> "#input")

    val inputValue = new GenericRecordBuilder(inputSchemaV1.rawSchema())
      .set("a", "aValue")
      .build()
    val inputConsumerRecord = wrapWithConsumerRecord(inputTopic, SchemaId.fromString(inputSchemaV1Props.getId), AvroUtils.serializeContainerToBytesArray(inputValue))

    val result = testRunner.runWithRawData(scenario, List(inputConsumerRecord))
    val (resultProducerRecord, resultSchemaIdHeader) = verifyOneSuccessRecord(result)
    resultSchemaIdHeader shouldEqual outputSchemaId
    val deserializedValue = AvroUtils.deserialize[GenericRecord](resultProducerRecord.value(), outputSchema.rawSchema())
    deserializedValue.get("a") shouldEqual "aValue"
    deserializedValue.get("b") shouldEqual bDefaultValue
  }

  private def verifyOneSuccessRecord(result: RunnerListResult[ProducerRecord[Array[Byte], Array[Byte]]]): (ProducerRecord[Array[Byte], Array[Byte]], SchemaId) = {
    val validResult = result.validValue
    validResult.errors shouldBe empty
    val successes = validResult.successes
    successes should have length 1
    val resultProducerRecord = successes.head
    val resultSchemaIdHeader = AzureUtils.extractSchemaId(resultProducerRecord.headers())
    (resultProducerRecord, resultSchemaIdHeader)
  }

  private def createRecordSchema(topicName: String,
                                 assemblyFields: SchemaBuilder.FieldAssembler[Schema] => SchemaBuilder.FieldAssembler[Schema]) = {
    val fields = SchemaBuilder
      .record(SchemaNameTopicMatchStrategy.valueSchemaNameFromTopicName(topicName))
      .namespace("not.important.namespace")
      .fields()
    new AvroSchema(assemblyFields(fields).endRecord())
  }

  private def wrapWithConsumerRecord(topic: String, schemaId: SchemaId, serializedValue: Array[Byte]) = {
    val inputHeaders = new RecordHeaders(Array[Header](AzureUtils.avroContentTypeHeader(schemaId)))
    new ConsumerRecord(topic, 0, 0, 0, TimestampType.CREATE_TIME,
      0, serializedValue.length, Array[Byte](), serializedValue, inputHeaders, Optional.empty[Integer]())
  }

}
