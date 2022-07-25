package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.scalatest.Assertion
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor6}
import pl.touk.nussknacker.engine.avro.RuntimeSchemaData
import pl.touk.nussknacker.engine.avro.helpers.{SchemaRegistryMixin, SimpleKafkaAvroSerializer}
import pl.touk.nussknacker.engine.avro.schema.{PaymentV1, PaymentV2}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.DefaultConfluentSchemaRegistryClient
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.universal.ConfluentUniversalKafkaDeserializer
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.universal.ConfluentUniversalKafkaSerde.ValueSchemaIdHeaderName
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.{ConfluentSchemaBasedSerdeProvider, ConfluentUtils}
import pl.touk.nussknacker.engine.kafka.ConsumerRecordUtils

import java.io.OutputStream

class ConfluentUniversalKafkaDeserializerTest extends SchemaRegistryMixin with TableDrivenPropertyChecks with ConfluentKafkaAvroSeDeSpecMixin {

  import MockSchemaRegistry._

  override protected def schemaRegistryClient: CSchemaRegistryClient = schemaRegistryMockClient

  private val confluentSchemaRegistryClient = new DefaultConfluentSchemaRegistryClient(MockSchemaRegistry.schemaRegistryMockClient)

  type CreateSetup = RuntimeSchemaData[ParsedSchema] => SchemaRegistryProviderSetup

  lazy val payloadWithSchemaIdSetup: CreateSetup = readerSchema => SchemaRegistryProviderSetup(SchemaRegistryProviderSetupType.avro,
    ConfluentSchemaBasedSerdeProvider.universal(MockSchemaRegistry.factory),
    new SimpleKafkaAvroSerializer(MockSchemaRegistry.schemaRegistryMockClient, isKey = false),
    new ConfluentUniversalKafkaDeserializer(confluentSchemaRegistryClient, Some(readerSchema), isKey = false))

  lazy val payloadWithoutSchemaIdSetup: CreateSetup = readerSchema => payloadWithSchemaIdSetup(readerSchema).copy(valueSerializer = new SimpleKafkaAvroSerializer(MockSchemaRegistry.schemaRegistryMockClient, isKey = false) {
    override def writeHeader(data: Any, avroSchema: Schema, schemaId: Int, out: OutputStream): Unit = ()
  })

  test("should properly deserialize record to object with same schema version") {
    val schemas = List(PaymentV1.schema)

    val table = Table[CreateSetup, Boolean, GenericRecord, GenericRecord, String, Boolean](
      ("setup", "schemaEvolution", "givenObj", "expectedObj", "topic", "shouldSendHeaders"),
      (payloadWithoutSchemaIdSetup, true, PaymentV1.record, PaymentV1.record, "simple.from-subject.headers", true),
      (payloadWithoutSchemaIdSetup, false, PaymentV1.record, PaymentV1.record, "simple.from-record.headers", true),
      (payloadWithSchemaIdSetup, true, PaymentV1.record, PaymentV1.record, "simple.from-subject.payload", false),
      (payloadWithSchemaIdSetup, false, PaymentV1.record, PaymentV1.record, "simple.from-record.payload", false),
      (payloadWithSchemaIdSetup, true, PaymentV1.record, PaymentV1.record, "simple.from-subject.headersAndPayload", true),
      (payloadWithSchemaIdSetup, false, PaymentV1.record, PaymentV1.record, "simple.from-record.headersAndPayload", true),
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
      (payloadWithSchemaIdSetup, true, PaymentV2.record, PaymentV1.record, "backwards.payload", false),
      (payloadWithSchemaIdSetup, true, PaymentV2.record, PaymentV1.record, "backwards.headersAndPayload", true),
    )

    runDeserializationTest(table, schemas)
  }


  private def runDeserializationTest(table: TableFor6[CreateSetup, Boolean, GenericRecord, GenericRecord, String, Boolean], schemas: List[Schema]): Assertion = {

    forAll(table) { (createSetup: CreateSetup, schemaEvolution: Boolean, givenObj: GenericRecord, expectedObj: GenericRecord, topic: String, shouldSendHeaders: Boolean) =>
      val topicConfig = createAndRegisterTopicConfig(topic, schemas)

      val inputSubject = ConfluentUtils.topicSubject(topicConfig.input, topicConfig.isKey)
      val inputSchemaId = schemaRegistryClient.getId(inputSubject, ConfluentUtils.convertToAvroSchema(expectedObj.getSchema))

      val expectedRuntimeSchemaData = RuntimeSchemaData(expectedObj.getSchema, Some(inputSchemaId))
      val schemaDataOpt = if (schemaEvolution) {
        Option(expectedRuntimeSchemaData)
      } else {
        None
      }
      val setup = createSetup(expectedRuntimeSchemaData.toParsedSchemaData)
      val deserializer = setup.provider.deserializationSchemaFactory.create(kafkaConfig, None, schemaDataOpt.map(_.toParsedSchemaData))

      val headers = if(shouldSendHeaders) {
        val givenObjSchemaId = schemaRegistryClient.getId(inputSubject, ConfluentUtils.convertToAvroSchema(givenObj.getSchema))
        ConsumerRecordUtils.toHeaders(Map(ValueSchemaIdHeaderName -> s"$givenObjSchemaId"))
      } else ConsumerRecordUtils.emptyHeaders
      setup.pushMessage(givenObj, topicConfig.input, headers = headers)

      setup.consumeAndVerifyMessages(deserializer, topicConfig.input, List(expectedObj))
    }

  }

}