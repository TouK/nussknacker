package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import io.circe.Json.{fromString, obj}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.record.TimestampType
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.json.JsonSchemaBuilder
import pl.touk.nussknacker.engine.kafka.{KafkaComponentsConfig, KafkaRecordUtils, UnspecializedTopicName}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.MockSchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.schemaid.SchemaIdFromNuHeadersPotentiallyShiftingConfluentPayload.ValueSchemaIdHeaderName

import java.nio.charset.StandardCharsets
import java.util.Optional

class UniversalToJsonFormatterSpec extends AnyFunSuite with Matchers with OptionValues {

  private lazy val rawKafkaConfig = ConfigFactory
    .empty()
    .withValue("kafkaProperties.\"bootstrap.servers\"", fromAnyRef("kafka_should_not_be_used:9092"))
    .withValue("kafkaProperties.\"schema.registry.url\"", fromAnyRef("schema_registry_should_not_be_used:8081"))

  private val kafkaComponentsConfig = KafkaComponentsConfig.parseConfig(rawKafkaConfig)

  private val schemaRegistryMockClient: MockSchemaRegistryClient = new MockSchemaRegistryClient

  private val formatter = {
    val schemaRegistryFactory = MockSchemaRegistryClientFactory.confluentBased(schemaRegistryMockClient)
    val serdeProvider =
      UniversalSchemaBasedSerdeProvider.create(
        schemaRegistryFactory,
        kafkaComponentsConfig
      )
    serdeProvider.recordFormatterFactory.create(schemaRegistryFactory.create(kafkaComponentsConfig))
  }

  test("json record formatting should work without schema") {
    val topic           = "no-schema-topic"
    val valueJsonString = obj("foo" -> fromString("bar")).noSpaces.getBytes(StandardCharsets.UTF_8)
    val record          = new ConsumerRecord[Array[Byte], Array[Byte]](topic, -1, -1, null, valueJsonString)

    val testData = formatter.prepareGeneratedTestData(List(record))

    testData.testRecords should have size 1
    val testRecord = testData.testRecords.head
    testRecord.json.hcursor.downField("keySchemaId").focus.value.isNull shouldBe true
    testRecord.json.hcursor.downField("valueSchemaId").focus.value.isNull shouldBe true
    testRecord.json.hcursor.downField("consumerRecord").downField("key").focus.value.isNull shouldBe true
    testRecord.json.hcursor
      .downField("consumerRecord")
      .downField("value")
      .downField("foo")
      .focus
      .value
      .asString
      .value shouldEqual "bar"
  }

  test("json record formatting should work with specified schema id") {
    val topic = UnspecializedTopicName("topic-with-json-schema")
    val jsonSchema = JsonSchemaBuilder.parseSchema("""{
        |  "type": "object",
        |  "properties": {
        |    "foo": {
        |      "type": "string"
        |    }
        |  },
        |  "additionalProperties": false
        |}""".stripMargin)
    val schemaId = schemaRegistryMockClient.register(
      ConfluentUtils.valueSubject(topic),
      ConfluentUtils.convertToJsonSchema(jsonSchema)
    )
    val valueJsonBytes = obj("foo" -> fromString("bar")).noSpaces.getBytes(StandardCharsets.UTF_8)
    val record = new ConsumerRecord[Array[Byte], Array[Byte]](
      topic.name,
      -1,
      -1L,
      -1L,
      TimestampType.NO_TIMESTAMP_TYPE,
      -1,
      -1,
      null: Array[Byte],
      valueJsonBytes,
      KafkaRecordUtils.toHeaders(Map(ValueSchemaIdHeaderName -> schemaId.toString)),
      Optional.empty[Integer]()
    )

    val testData = formatter.prepareGeneratedTestData(List(record))

    testData.testRecords should have size 1
    val testRecord = testData.testRecords.head
    testRecord.json.hcursor.downField("keySchemaId").focus.value.isNull shouldBe true
    testRecord.json.hcursor.downField("valueSchemaId").focus.value.asNumber.value.toInt.value shouldBe schemaId
    testRecord.json.hcursor.downField("consumerRecord").downField("key").focus.value.isNull shouldBe true
    testRecord.json.hcursor
      .downField("consumerRecord")
      .downField("value")
      .downField("foo")
      .focus
      .value
      .asString
      .value shouldEqual "bar"
  }

}
