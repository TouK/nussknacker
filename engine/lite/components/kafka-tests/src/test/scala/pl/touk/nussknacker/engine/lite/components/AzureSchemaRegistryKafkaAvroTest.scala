package pl.touk.nussknacker.engine.lite.components

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.{Schema, SchemaBuilder}
import org.apache.avro.generic.{GenericRecord, GenericRecordBuilder}
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.ProducerRecord
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.tags.Network
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils, UnspecializedTopicName}
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName.ToUnspecializedTopicName
import pl.touk.nussknacker.engine.lite.components.LiteKafkaComponentProvider.KafkaUniversalName
import pl.touk.nussknacker.engine.lite.util.test.{KafkaAvroConsumerRecord, KafkaConsumerRecord}
import pl.touk.nussknacker.engine.lite.util.test.LiteKafkaTestScenarioRunner._
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.{
  schemaVersionParamName,
  sinkKeyParamName,
  sinkRawEditorParamName,
  sinkValueParamName,
  topicParamName
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaId, SchemaVersionOption}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.{
  AzureSchemaRegistryClientFactory,
  AzureUtils,
  SchemaNameTopicMatchStrategy
}
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.RunnerListResult
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

import java.util.Collections
import java.util.concurrent.TimeUnit
import scala.util.control.NonFatal

// TODO: make this test use mocked schema registry instead of the real one
@Network
class AzureSchemaRegistryKafkaAvroTest
    extends AnyFunSuite
    with Matchers
    with ValidatedValuesDetailedMessage
    with LazyLogging {

  private val schemaRegistryClientFactory = AzureSchemaRegistryClientFactory

  private val eventHubsNamespace = Option(System.getenv("AZURE_EVENT_HUBS_NAMESPACE")).getOrElse("nu-cloud")
  private val eventHubsSharedAccessKeyName =
    Option(System.getenv("AZURE_EVENT_HUBS_SHARED_ACCESS_KEY_NAME")).getOrElse("unknown")
  private val eventHubsSharedAccessKey =
    Option(System.getenv("AZURE_EVENT_HUBS_SHARED_ACCESS_KEY")).getOrElse("unknown")

  // See https://nussknacker.io/documentation/cloud/azure/#setting-up-nussknacker-cloud
  private val config = ConfigFactory.parseString(s"""
       | kafka {
       |   kafkaProperties {
       |     "bootstrap.servers"  : "$eventHubsNamespace.servicebus.windows.net:9093"
       |     "security.protocol"  : "SASL_SSL"
       |     "sasl.mechanism"     : "PLAIN"
       |     "sasl.jaas.config"   : "org.apache.kafka.common.security.plain.PlainLoginModule required username=\\"$$ConnectionString\\" password=\\"Endpoint=sb://$eventHubsNamespace.servicebus.windows.net/;SharedAccessKeyName=$eventHubsSharedAccessKeyName;SharedAccessKey=$eventHubsSharedAccessKey\\";"
       |     "schema.registry.url": "https://$eventHubsNamespace.servicebus.windows.net"
       |     "schema.group"       : "test-group"
       |   }
       | }
       |""".stripMargin)

  private val kafkaConfig = KafkaConfig.parseConfig(config)

  private val testRunner =
    TestScenarioRunner
      .kafkaLiteBased(config)
      .withSchemaRegistryClientFactory(schemaRegistryClientFactory)
      .build()

  private val schemaRegistryClient =
    schemaRegistryClientFactory.create(kafkaConfig.schemaRegistryClientKafkaConfig)

  test("round-trip Avro serialization using Azure Schema Registry") {
    val (
      inputTopic: TopicName.ForSource,
      inputSchema: Schema,
      inputSchemaId: SchemaId,
      outputTopic: TopicName.ForSink,
      outputSchema: Schema,
      outputSchemaId: SchemaId,
      scenario: CanonicalProcess
    ) = prepareAvroSetup

    registerTopic(List(inputTopic, outputTopic).map(_.toUnspecialized))

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
    val (
      inputTopic,
      _: Schema,
      _: SchemaId,
      outputTopic,
      _: Schema,
      outputSchemaId: SchemaId,
      scenario: CanonicalProcess
    ) =
      prepareAvroSetup

    registerTopic(List(inputTopic, outputTopic).map(_.toUnspecialized))

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
    val inputTopic   = TopicName.ForSource(s"$scenarioName-input")
    val outputTopic  = TopicName.ForSink(s"$scenarioName-output")

    val aFieldOnly   = (assembler: SchemaBuilder.FieldAssembler[Schema]) => assembler.requiredString("a")
    val inputSchema  = createRecordSchema(inputTopic.toUnspecialized, aFieldOnly)
    val outputSchema = createRecordSchema(outputTopic.toUnspecialized, aFieldOnly)

    val inputSchemaId  = testRunner.registerAvroSchema(inputTopic.toUnspecialized, inputSchema)
    val outputSchemaId = testRunner.registerAvroSchema(outputTopic.toUnspecialized, outputSchema)

    val scenario = ScenarioBuilder
      .streamingLite(scenarioName)
      .source(
        "source",
        KafkaUniversalName,
        topicParamName.value         -> s"'${inputTopic.name}'".spel,
        schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel
      )
      .emptySink(
        "sink",
        KafkaUniversalName,
        topicParamName.value         -> s"'${outputTopic.name}'".spel,
        schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel,
        sinkKeyParamName.value       -> "".spel,
        sinkRawEditorParamName.value -> "true".spel,
        sinkValueParamName.value     -> "#input".spel
      )
    (inputTopic, inputSchema, inputSchemaId, outputTopic, outputSchema, outputSchemaId, scenario)
  }

  test("round-trip Avro serialization with primitive types on Azure") {
    val scenarioName = "primitive"
    val inputTopic   = TopicName.ForSource(s"$scenarioName-input")
    val outputTopic  = TopicName.ForSink(s"$scenarioName-output")

    registerTopic(List(inputTopic, outputTopic).map(_.toUnspecialized))

    val inputSchema  = SchemaBuilder.builder().intType()
    val outputSchema = SchemaBuilder.builder().longType()

    val inputSchemaId  = testRunner.registerAvroSchema(inputTopic.toUnspecialized, inputSchema)
    val outputSchemaId = testRunner.registerAvroSchema(outputTopic.toUnspecialized, outputSchema)

    val scenario = ScenarioBuilder
      .streamingLite(scenarioName)
      .source(
        "source",
        KafkaUniversalName,
        topicParamName.value         -> s"'${inputTopic.name}'".spel,
        schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel
      )
      .emptySink(
        "sink",
        KafkaUniversalName,
        topicParamName.value         -> s"'${outputTopic.name}'".spel,
        schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel,
        sinkKeyParamName.value       -> "".spel,
        sinkRawEditorParamName.value -> "true".spel,
        sinkValueParamName.value     -> "#input".spel
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
    val inputTopic   = TopicName.ForSource(s"$scenarioName-input")
    val outputTopic  = TopicName.ForSink(s"$scenarioName-output")

    registerTopic(List(inputTopic, outputTopic).map(_.toUnspecialized))

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
    val inputSchemaV1 = createRecordSchema(inputTopic.toUnspecialized, aFieldOnly)
    val inputSchemaV2 = createRecordSchema(inputTopic.toUnspecialized, abFields)
    val outputSchema  = createRecordSchema(outputTopic.toUnspecialized, abOutputFields)

    val inputSchemaV1Id = testRunner.registerAvroSchema(inputTopic.toUnspecialized, inputSchemaV1)
    // TODO: maybe we should return this version in testRunner.registerAvroSchema as well?
    val inputSchemaV2Props = schemaRegistryClient.registerSchemaVersionIfNotExists(new AvroSchema(inputSchemaV2))
    val outputSchemaId     = testRunner.registerAvroSchema(outputTopic.toUnspecialized, outputSchema)

    val scenario = ScenarioBuilder
      .streamingLite(scenarioName)
      .source(
        "source",
        KafkaUniversalName,
        topicParamName.value         -> s"'${inputTopic.name}'".spel,
        schemaVersionParamName.value -> s"'${inputSchemaV2Props.getVersion}'".spel
      )
      .filter("filter-b-default", s"#input.b == '$bDefaultValue'".spel)
      .emptySink(
        "sink",
        KafkaUniversalName,
        topicParamName.value         -> s"'${outputTopic.name}'".spel,
        schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel,
        sinkKeyParamName.value       -> "".spel,
        sinkRawEditorParamName.value -> "true".spel,
        sinkValueParamName.value     -> "{a: #input.a, b: #input.b + 'xyz'}".spel
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
      topicName: UnspecializedTopicName,
      assemblyFields: SchemaBuilder.FieldAssembler[Schema] => SchemaBuilder.FieldAssembler[Schema]
  ) = {
    val fields = SchemaBuilder
      .record(SchemaNameTopicMatchStrategy.valueSchemaNameFromTopicName(topicName))
      .namespace("not.important.namespace")
      .fields()
    assemblyFields(fields).endRecord()
  }

  private def registerTopic(topicNames: List[UnspecializedTopicName], retries: Int = 3): Unit = {
    import scala.jdk.CollectionConverters._
    logger.info(s"Register topics ${topicNames.map(_.name).mkString(",")}, retries = $retries")
    KafkaUtils.usingAdminClient(kafkaConfig) { admin =>
      val existingTopics     = admin.listTopics().names().get().asScala.toList.map(UnspecializedTopicName.apply)
      val (toSkip, toCreate) = topicNames.partition(existingTopics.contains)
      logger.info(s"Skip existing topics: ${toSkip.map(_.name).mkString(",")}")
      if (toCreate.nonEmpty) {
        val topicsToCreate = toCreate.map(topic => new NewTopic(topic.name, Collections.emptyMap())).asJavaCollection
        try {
          logger.info(s"Create topics: ${topicNames.map(_.name).mkString(",")}")
          admin.createTopics(topicsToCreate).all().get(5, TimeUnit.SECONDS)
        } catch {
          case NonFatal(e) =>
            logger.error(s"Other exception for ${topicNames.map(_.name).mkString(",")}", e)
            if (retries > 0) {
              Thread.sleep(1000)
              registerTopic(toCreate, retries - 1)
            }
        }
      }
    }
  }

}
