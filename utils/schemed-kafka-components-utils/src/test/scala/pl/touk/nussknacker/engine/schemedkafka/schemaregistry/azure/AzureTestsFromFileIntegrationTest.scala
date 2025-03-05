package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure

import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.{Schema, SchemaBuilder}
import org.apache.avro.generic.{GenericRecord, GenericRecordBuilder}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.Header
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.tags.Network
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, UnspecializedTopicName}
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName.ToUnspecializedTopicName
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaId
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.{
  UniversalSchemaBasedSerdeProvider,
  UniversalSchemaRegistryClientFactory
}
import pl.touk.nussknacker.test.EitherValuesDetailedMessage

import java.nio.charset.StandardCharsets
import java.util.Optional

@Network
class AzureTestsFromFileIntegrationTest
    extends AnyFunSuite
    with Matchers
    with OptionValues
    with EitherValuesDetailedMessage
    with LazyLogging {

  private val eventHubsNamespace = Option(System.getenv("AZURE_EVENT_HUBS_NAMESPACE")).getOrElse("nu-cloud")

  private val schemaRegistryConfigMap =
    Map("schema.registry.url" -> s"https://$eventHubsNamespace.servicebus.windows.net", "schema.group" -> "test-group")

  private val kafkaConfig = KafkaConfig(Some(schemaRegistryConfigMap), None, showTopicsWithoutSchema = false)

  test("test from file round-trip") {
    val schemaRegistryClient = AzureSchemaRegistryClientFactory.create(kafkaConfig.schemaRegistryClientKafkaConfig)
    val serdeProvider        = UniversalSchemaBasedSerdeProvider.create(UniversalSchemaRegistryClientFactory)
    val factory   = serdeProvider.deserializationSchemaFactory.create[String, GenericRecord](kafkaConfig, None, None)
    val formatter = serdeProvider.recordFormatterFactory.create[String, GenericRecord](kafkaConfig, factory)

    val topic         = TopicName.ForSource("avro-testfromfile")
    val aFieldOnly    = (assembler: SchemaBuilder.FieldAssembler[Schema]) => assembler.requiredString("a")
    val schemaV1      = createRecordSchema(topic.toUnspecialized, aFieldOnly)
    val schemaV1Props = schemaRegistryClient.registerSchemaVersionIfNotExists(schemaV1)

    val key = "fooKey"
    val value = new GenericRecordBuilder(schemaV1.rawSchema())
      .set("a", "aValue")
      .build()
    val schemaId = SchemaId.fromString(schemaV1Props.getId)
    val cr       = wrapWithConsumerRecord(topic, key, schemaId, AvroUtils.serializeContainerToBytesArray(value))

    val testData = formatter.prepareGeneratedTestData(List(cr))
    testData.testRecords should have size 1
    val testRecord = testData.testRecords.head

    testRecord.json.hcursor.downField("keySchemaId").focus.value.isNull shouldBe true
    testRecord.json.hcursor.downField("valueSchemaId").as[String].rightValue shouldEqual schemaId.asString
    testRecord.json.hcursor.downField("consumerRecord").downField("key").as[String].rightValue shouldEqual key
    val valueJson = testRecord.json.hcursor.downField("consumerRecord").downField("value").focus.get
    logger.debug(s"test data value: $valueJson")

    val crFromTestData = formatter.parseRecord(topic, testRecord)
    crFromTestData.key() shouldEqual cr.key()
    crFromTestData.value() shouldEqual cr.value()
    crFromTestData.headers() shouldEqual cr.headers()
  }

  private def createRecordSchema(
      topicName: UnspecializedTopicName,
      assemblyFields: SchemaBuilder.FieldAssembler[Schema] => SchemaBuilder.FieldAssembler[Schema]
  ) = {
    val fields = SchemaBuilder
      .record(SchemaNameTopicMatchStrategy.valueSchemaNameFromTopicName(topicName))
      .namespace("not.important.namespace")
      .fields()
    new AvroSchema(assemblyFields(fields).endRecord())
  }

  private def wrapWithConsumerRecord(
      topic: TopicName.ForSource,
      key: String,
      schemaId: SchemaId,
      serializedValue: Array[Byte]
  ) = {
    val keyBytes     = key.getBytes(StandardCharsets.UTF_8)
    val inputHeaders = new RecordHeaders(Array[Header](AzureUtils.avroContentTypeHeader(schemaId)))
    new ConsumerRecord(
      topic.name,
      0,
      0,
      0,
      TimestampType.CREATE_TIME,
      keyBytes.length,
      serializedValue.length,
      keyBytes,
      serializedValue,
      inputHeaders,
      Optional.empty[Integer]()
    )
  }

}
