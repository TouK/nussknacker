package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.header.Headers
import org.scalatest.Assertion
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor6}
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.kafka.KafkaRecordUtils
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName.ToUnspecializedTopicName
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.helpers.{SchemaRegistryMixin, SimpleKafkaAvroSerializer}
import pl.touk.nussknacker.engine.schemedkafka.schema.{PaymentV1, PaymentV2}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.DefaultConfluentSchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.schemaid.SchemaIdFromNuHeadersPotentiallyShiftingConfluentPayload.ValueSchemaIdHeaderName
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.{
  UniversalKafkaDeserializer,
  UniversalSchemaBasedSerdeProvider
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{ChainedSchemaIdFromMessageExtractor, SchemaId}

import java.io.OutputStream

class UniversalKafkaDeserializerTest
    extends SchemaRegistryMixin
    with TableDrivenPropertyChecks
    with ConfluentKafkaAvroSeDeSpecMixin {

  import MockSchemaRegistry._

  override protected def schemaRegistryClient: CSchemaRegistryClient = schemaRegistryMockClient

  private val confluentSchemaRegistryClient = new DefaultConfluentSchemaRegistryClient(
    MockSchemaRegistry.schemaRegistryMockClient
  )

  type CreateSetup = RuntimeSchemaData[ParsedSchema] => SchemaRegistryProviderSetup

  private val schemaIdExtractor: ChainedSchemaIdFromMessageExtractor =
    UniversalSchemaBasedSerdeProvider.createSchemaIdFromMessageExtractor(isConfluent = true, isAzure = false)

  lazy val payloadWithSchemaIdSetup: CreateSetup = readerSchema =>
    SchemaRegistryProviderSetup(
      SchemaRegistryProviderSetupType.avro,
      UniversalSchemaBasedSerdeProvider.create(MockSchemaRegistry.factory),
      new SimpleKafkaAvroSerializer(MockSchemaRegistry.schemaRegistryMockClient, isKey = false),
      new UniversalKafkaDeserializer(
        confluentSchemaRegistryClient,
        kafkaConfig,
        schemaIdExtractor,
        Some(readerSchema),
        isKey = false
      )
    )

  lazy val payloadWithoutSchemaIdSetup: CreateSetup = readerSchema =>
    payloadWithSchemaIdSetup(readerSchema).copy(valueSerializer =
      new SimpleKafkaAvroSerializer(MockSchemaRegistry.schemaRegistryMockClient, isKey = false) {
        override def writeHeader(schemaId: SchemaId, out: OutputStream, headers: Headers): Unit = ()
      }
    )

  test("should properly deserialize record to object with same schema version") {
    val schemas = List(PaymentV1.schema)

    val table = Table[CreateSetup, Boolean, GenericRecord, GenericRecord, String, Boolean](
      ("setup", "schemaEvolution", "givenObj", "expectedObj", "topic", "shouldSendHeaders"),
      (payloadWithoutSchemaIdSetup, true, PaymentV1.record, PaymentV1.record, "simple.from-subject.headers", true),
      (payloadWithoutSchemaIdSetup, false, PaymentV1.record, PaymentV1.record, "simple.from-record.headers", true),
      (
        payloadWithoutSchemaIdSetup,
        true,
        PaymentV1.record,
        PaymentV1.record,
        "simple.from-subject.no-schema-id",
        false
      ),
      (payloadWithSchemaIdSetup, true, PaymentV1.record, PaymentV1.record, "simple.from-subject.payload", false),
      (payloadWithSchemaIdSetup, false, PaymentV1.record, PaymentV1.record, "simple.from-record.payload", false),
      (
        payloadWithSchemaIdSetup,
        true,
        PaymentV1.record,
        PaymentV1.record,
        "simple.from-subject.headersAndPayload",
        true
      ),
      (
        payloadWithSchemaIdSetup,
        false,
        PaymentV1.record,
        PaymentV1.record,
        "simple.from-record.headersAndPayload",
        true
      ),
    )

    runDeserializationTest(table, schemas)
  }

  test("should properly deserialize record to object with newer compatible schema version") {
    val schemas = List(PaymentV1.schema, PaymentV2.schema)

    val table = Table[CreateSetup, Boolean, GenericRecord, GenericRecord, String, Boolean](
      ("setup", "schemaEvolution", "givenObj", "expectedObj", "topic", "shouldSendHeaders"),
      (payloadWithoutSchemaIdSetup, true, PaymentV1.record, PaymentV2.record, "forward.headers", true),
      (payloadWithSchemaIdSetup, true, PaymentV1.record, PaymentV2.record, "forward.payload", false),
      (payloadWithSchemaIdSetup, true, PaymentV1.record, PaymentV2.record, "forward.headersAndPayload", true),
    )

    runDeserializationTest(table, schemas)
  }

  test("should properly deserialize record to object with older compatible schema version") {
    val schemas = List(PaymentV1.schema, PaymentV2.schema)

    val table = Table[CreateSetup, Boolean, GenericRecord, GenericRecord, String, Boolean](
      ("setup", "schemaEvolution", "givenObj", "expectedObj", "topic", "shouldSendHeaders"),
      (payloadWithoutSchemaIdSetup, true, PaymentV2.record, PaymentV1.record, "backwards.headers", true),
      (payloadWithoutSchemaIdSetup, true, PaymentV2.record, PaymentV1.record, "backwards.no-schema-id", false),
      (payloadWithSchemaIdSetup, true, PaymentV2.record, PaymentV1.record, "backwards.payload", false),
      (payloadWithSchemaIdSetup, true, PaymentV2.record, PaymentV1.record, "backwards.headersAndPayload", true),
    )

    runDeserializationTest(table, schemas)
  }

  private def runDeserializationTest(
      table: TableFor6[CreateSetup, Boolean, GenericRecord, GenericRecord, String, Boolean],
      schemas: List[Schema]
  ): Assertion = {

    forAll(table) {
      (
          createSetup: CreateSetup,
          schemaEvolution: Boolean,
          givenObj: GenericRecord,
          expectedObj: GenericRecord,
          topic: String,
          shouldSendHeaders: Boolean
      ) =>
        val topicConfig = createAndRegisterTopicConfig(topic, schemas)

        val inputSubject = ConfluentUtils.topicSubject(topicConfig.input.toUnspecialized, topicConfig.isKey)
        val inputSchemaId = SchemaId.fromInt(
          schemaRegistryClient.getId(inputSubject, ConfluentUtils.convertToAvroSchema(expectedObj.getSchema))
        )

        val expectedRuntimeSchemaData = RuntimeSchemaData(expectedObj.getSchema, Some(inputSchemaId))
        val schemaDataOpt = if (schemaEvolution) {
          Option(expectedRuntimeSchemaData)
        } else {
          None
        }
        val setup = createSetup(expectedRuntimeSchemaData.toParsedSchemaData)
        val deserializer =
          setup.provider.deserializationSchemaFactory.create(kafkaConfig, None, schemaDataOpt.map(_.toParsedSchemaData))

        val headers = if (shouldSendHeaders) {
          val givenObjSchemaId =
            schemaRegistryClient.getId(inputSubject, ConfluentUtils.convertToAvroSchema(givenObj.getSchema))
          KafkaRecordUtils.toHeaders(Map(ValueSchemaIdHeaderName -> s"$givenObjSchemaId"))
        } else KafkaRecordUtils.emptyHeaders
        setup.pushMessage(givenObj, topicConfig.input, headers = headers)

        setup.consumeAndVerifyMessages(deserializer, TopicName.ForSink(topicConfig.input.name), List(expectedObj))
    }

  }

}
