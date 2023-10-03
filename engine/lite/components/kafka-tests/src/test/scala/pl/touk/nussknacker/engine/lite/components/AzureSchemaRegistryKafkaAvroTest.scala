package pl.touk.nussknacker.engine.lite.components

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import io.circe.Json
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.generic.{GenericRecord, GenericRecordBuilder}
import org.apache.avro.{Schema, SchemaBuilder}
import org.apache.kafka.clients.producer.ProducerRecord
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.tags.Network
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.lite.components.LiteKafkaComponentProvider.KafkaUniversalName
import pl.touk.nussknacker.engine.lite.util.test.{KafkaAvroConsumerRecord, KafkaConsumerRecord}
import pl.touk.nussknacker.engine.lite.util.test.LiteKafkaTestScenarioRunner._
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.{
  SchemaVersionParamName,
  SinkKeyParamName,
  SinkRawEditorParamName,
  SinkValueParamName,
  TopicParamName
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.{
  AzureSchemaRegistryClientFactory,
  AzureUtils,
  SchemaNameTopicMatchStrategy
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaId, SchemaVersionOption}
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.RunnerListResult
import pl.touk.nussknacker.test.{KafkaConfigProperties, ValidatedValuesDetailedMessage}

// TODO: make this test use mocked schema registry instead of the real one
@Network
class AzureSchemaRegistryKafkaAvroTest extends AnyFunSuite with Matchers with ValidatedValuesDetailedMessage {

  private val schemaRegistryClientFactory = AzureSchemaRegistryClientFactory

  private val eventHubsNamespace = Option(System.getenv("AZURE_EVENT_HUBS_NAMESPACE")).getOrElse("nu-cloud")
  private val config = ConfigFactory
    .empty()
    .withValue(
      KafkaConfigProperties.property("schema.registry.url"),
      fromAnyRef(s"https://$eventHubsNamespace.servicebus.windows.net")
    )
    .withValue(KafkaConfigProperties.property("schema.group"), fromAnyRef("test-group"))

  private val testRunner =
    TestScenarioRunner
      .kafkaLiteBased(config)
      .withSchemaRegistryClientFactory(schemaRegistryClientFactory)
      .build()

  private val schemaRegistryClient =
    schemaRegistryClientFactory.create(KafkaConfig.parseConfig(config).schemaRegistryClientKafkaConfig)

  test("round-trip Avro serialization using Azure Schema Registry") {
    val (
      inputTopic: String,
      inputSchema: Schema,
      inputSchemaId: SchemaId,
      outputSchema: Schema,
      outputSchemaId: SchemaId,
      scenario: CanonicalProcess
    ) = prepareAvroSetup

    val inputValue = new GenericRecordBuilder(inputSchema)
      .set("a", "aValue")
      .build()
    val inputConsumerRecord = KafkaAvroConsumerRecord(inputTopic, inputValue, inputSchemaId)

    val result = testRunner.runWithAvroData[String, GenericRecord](scenario, List(inputConsumerRecord))
    val (resultProducerRecord, resultSchemaIdHeader) = verifyOneSuccessRecord(result)
    val expectedValue = new GenericRecordBuilder(outputSchema)
      .set("a", "aValue")
      .build()
    resultProducerRecord.value() shouldEqual expectedValue
    resultSchemaIdHeader shouldEqual outputSchemaId
  }

  test("round-trip Avro schema with json payload serialization on Azure") {
    val (inputTopic: String, _: Schema, _: SchemaId, _: Schema, outputSchemaId: SchemaId, scenario: CanonicalProcess) =
      prepareAvroSetup

    val jsonPayloadTestRunner = TestScenarioRunner
      .kafkaLiteBased(config.withValue("kafka.avroAsJsonSerialization", fromAnyRef(true)))
      .withSchemaRegistryClientFactory(schemaRegistryClientFactory)
      .build()

    val inputValue          = Json.fromFields(Map("a" -> Json.fromString("aValue"))).noSpaces
    val inputConsumerRecord = KafkaConsumerRecord[String, String](inputTopic, inputValue)

    val result = jsonPayloadTestRunner.runWithStringData(scenario, List(inputConsumerRecord))
    val (resultProducerRecord, resultSchemaIdHeader) = verifyOneSuccessRecord(result)
    resultProducerRecord.value() shouldEqual inputValue
    resultSchemaIdHeader shouldEqual outputSchemaId
  }

  private def prepareAvroSetup = {
    val scenarioName = "avro"
    val inputTopic   = s"$scenarioName-input"
    val outputTopic  = s"$scenarioName-output"

    val aFieldOnly   = (assembler: SchemaBuilder.FieldAssembler[Schema]) => assembler.requiredString("a")
    val inputSchema  = createRecordSchema(inputTopic, aFieldOnly)
    val outputSchema = createRecordSchema(outputTopic, aFieldOnly)

    val inputSchemaId  = testRunner.registerAvroSchema(inputTopic, inputSchema)
    val outputSchemaId = testRunner.registerAvroSchema(outputTopic, outputSchema)

    val scenario = ScenarioBuilder
      .streamingLite(scenarioName)
      .source(
        "source",
        KafkaUniversalName,
        TopicParamName         -> s"'$inputTopic'",
        SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'"
      )
      .emptySink(
        "sink",
        KafkaUniversalName,
        TopicParamName         -> s"'$outputTopic'",
        SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'",
        SinkKeyParamName       -> "",
        SinkRawEditorParamName -> "true",
        SinkValueParamName     -> "#input"
      )
    (inputTopic, inputSchema, inputSchemaId, outputSchema, outputSchemaId, scenario)
  }

  test("round-trip Avro serialization with primitive types on Azure") {
    val scenarioName = "primitive"
    val inputTopic   = s"$scenarioName-input"
    val outputTopic  = s"$scenarioName-output"

    val inputSchema  = SchemaBuilder.builder().intType()
    val outputSchema = SchemaBuilder.builder().longType()

    val inputSchemaId  = testRunner.registerAvroSchema(inputTopic, inputSchema)
    val outputSchemaId = testRunner.registerAvroSchema(outputTopic, outputSchema)

    val scenario = ScenarioBuilder
      .streamingLite(scenarioName)
      .source(
        "source",
        KafkaUniversalName,
        TopicParamName         -> s"'$inputTopic'",
        SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'"
      )
      .emptySink(
        "sink",
        KafkaUniversalName,
        TopicParamName         -> s"'$outputTopic'",
        SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'",
        SinkKeyParamName       -> "",
        SinkRawEditorParamName -> "true",
        SinkValueParamName     -> "#input"
      )

    val inputValue          = 123
    val inputConsumerRecord = KafkaAvroConsumerRecord(inputTopic, inputValue, inputSchemaId)

    val result = testRunner.runWithAvroData[String, Any](scenario, List(inputConsumerRecord))
    val (resultProducerRecord, resultSchemaIdHeader) = verifyOneSuccessRecord(result)
    val expectedValue                                = 123L
    resultProducerRecord.value() shouldEqual expectedValue
    resultSchemaIdHeader shouldEqual outputSchemaId
  }

  test("schema evolution in Avro source using Azure Schema Registry") {
    val scenarioName = "avro-schemaevolution"
    val inputTopic   = s"$scenarioName-input"
    val outputTopic  = s"$scenarioName-output"

    val aFieldOnly    = (assembler: SchemaBuilder.FieldAssembler[Schema]) => assembler.requiredString("a")
    val bDefaultValue = "bDefault"
    val abFields = (assembler: SchemaBuilder.FieldAssembler[Schema]) =>
      assembler
        .requiredString("a")
        .name("b")
        .`type`()
        .stringType()
        .stringDefault(bDefaultValue)
    val abOutputFields = (assembler: SchemaBuilder.FieldAssembler[Schema]) =>
      assembler
        .requiredString("a")
        .requiredString("b")
    val inputSchemaV1 = createRecordSchema(inputTopic, aFieldOnly)
    val inputSchemaV2 = createRecordSchema(inputTopic, abFields)
    val outputSchema  = createRecordSchema(outputTopic, abOutputFields)

    val inputSchemaV1Id = testRunner.registerAvroSchema(inputTopic, inputSchemaV1)
    // TODO: maybe we should return this version in testRunner.registerAvroSchema as well?
    val inputSchemaV2Props = schemaRegistryClient.registerSchemaVersionIfNotExists(new AvroSchema(inputSchemaV2))
    val outputSchemaId     = testRunner.registerAvroSchema(outputTopic, outputSchema)

    val scenario = ScenarioBuilder
      .streamingLite(scenarioName)
      .source(
        "source",
        KafkaUniversalName,
        TopicParamName         -> s"'$inputTopic'",
        SchemaVersionParamName -> s"'${inputSchemaV2Props.getVersion}'"
      )
      .filter("filter-b-default", s"#input.b == '$bDefaultValue'")
      .emptySink(
        "sink",
        KafkaUniversalName,
        TopicParamName         -> s"'$outputTopic'",
        SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'",
        SinkKeyParamName       -> "",
        SinkRawEditorParamName -> "true",
        SinkValueParamName     -> "{a: #input.a, b: #input.b + 'xyz'}"
      )

    val inputValue = new GenericRecordBuilder(inputSchemaV1)
      .set("a", "aValue")
      .build()
    val inputConsumerRecord = KafkaAvroConsumerRecord(inputTopic, inputValue, inputSchemaV1Id)

    val result = testRunner.runWithAvroData[String, GenericRecord](scenario, List(inputConsumerRecord))
    val (resultProducerRecord, resultSchemaIdHeader) = verifyOneSuccessRecord(result)
    resultSchemaIdHeader shouldEqual outputSchemaId
    resultProducerRecord.value().get("a") shouldEqual "aValue"
    resultProducerRecord.value().get("b") shouldEqual bDefaultValue + "xyz"
  }

  private def verifyOneSuccessRecord[K, V](
      result: RunnerListResult[ProducerRecord[K, V]]
  ): (ProducerRecord[K, V], SchemaId) = {
    val validResult = result.validValue
    validResult.errors shouldBe empty
    val successes = validResult.successes
    successes should have length 1
    val resultProducerRecord = successes.head
    val resultSchemaIdHeader = AzureUtils.extractSchemaId(resultProducerRecord.headers())
    (resultProducerRecord, resultSchemaIdHeader)
  }

  private def createRecordSchema(
      topicName: String,
      assemblyFields: SchemaBuilder.FieldAssembler[Schema] => SchemaBuilder.FieldAssembler[Schema]
  ) = {
    val fields = SchemaBuilder
      .record(SchemaNameTopicMatchStrategy.valueSchemaNameFromTopicName(topicName))
      .namespace("not.important.namespace")
      .fields()
    assemblyFields(fields).endRecord()
  }

}
