package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.apache.flink.formats.avro.typeutils.NkSerializableAvroSchema
import org.scalatest.Assertion
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor5}
import pl.touk.nussknacker.engine.avro.helpers.KafkaAvroSpecMixin
import pl.touk.nussknacker.engine.avro.schema.{AvroSchemaEvolutionException, FullNameV1, PaymentV1, PaymentV2}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

class ConfluentKafkaAvroSerializationSpec extends KafkaAvroSpecMixin with TableDrivenPropertyChecks with ConfluentKafkaAvroSeDeSpecMixin {

  import MockSchemaRegistry._

  override protected def schemaRegistryClient: CSchemaRegistryClient = schemaRegistryMockClient

  override protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory = factory

  override protected lazy val schemaRegistryProvider: ConfluentSchemaRegistryProvider = ConfluentSchemaRegistryProvider.avroPayload(confluentClientFactory)

  private val encode = BestEffortJsonEncoder(failOnUnkown = false).encode _
  
  test("should properly serialize avro object to record with same schema version") {
    val schemas = List(PaymentV1.schema)
    val version = Some(1)

    val table = Table[SchemaRegistryProviderSetup, Option[Schema], GenericRecord, Any, String](
      ("setup", "schemaEvolution", "givenObj", "expectedObj", "topic"),
      (avroSetup, None, PaymentV1.record, PaymentV1.record, "simple.from-record"),
      (avroSetup, Some(PaymentV1.schema), PaymentV1.record, PaymentV1.record, "simple.from-subject-version"),
      (jsonSetup, None, PaymentV1.record, encode(PaymentV1.exampleData), "json.simple.from-record"),
      (jsonSetup, Some(PaymentV1.schema), PaymentV1.record, encode(PaymentV1.exampleData), "json.simple.from-subject-version")

    )

    runSerializationTest(table, version, schemas)
  }

  test("should properly serialize avro object to record with newer compatible schema version") {
    val schemas = List(PaymentV1.schema, PaymentV2.schema)
    val version = Some(2)

    val table = Table[SchemaRegistryProviderSetup, Option[Schema], GenericRecord, Any, String](
      ("setup", "schemaEvolution", "givenObj", "expectedObj", "topic"),
      (avroSetup, None, PaymentV1.record, PaymentV1.record, "forward.from-record"),
      (avroSetup, Some(PaymentV2.schema), PaymentV1.record, PaymentV2.record, "forward.from-subject-version"),
      (jsonSetup, None, PaymentV1.record, encode(PaymentV1.exampleData), "json.forward.from-record"),
      (jsonSetup, Some(PaymentV2.schema), PaymentV1.record, encode(PaymentV2.exampleData), "json.forward.from-subject-version")
    )

    runSerializationTest(table, version, schemas)
  }

  test("should properly serialize avro object to record with latest compatible schema version") {
    val schemas = List(PaymentV1.schema, PaymentV2.schema)
    val version = None

    val table = Table[SchemaRegistryProviderSetup, Option[Schema], GenericRecord, Any, String](
      ("setup", "schemaEvolution", "givenObj", "expectedObj", "topic"),
      (avroSetup, None, PaymentV1.record, PaymentV1.record, "forward.latest.from-record"),
      (avroSetup, Some(PaymentV2.schema), PaymentV1.record, PaymentV2.record, "forward.latest.from-subject-version"),
      (jsonSetup, None, PaymentV1.record, encode(PaymentV1.exampleData), "json.forward.latest.from-record"),
      (jsonSetup, Some(PaymentV2.schema), PaymentV1.record, encode(PaymentV2.exampleData), "json.forward.latest.from-subject-version")
    )

    runSerializationTest(table, version, schemas)
  }

  test("should properly serialize avro object to record with older compatible schema version") {
    val schemas = List(PaymentV1.schema, PaymentV2.schema)
    val version = Some(1)

    val table = Table[SchemaRegistryProviderSetup, Option[Schema], GenericRecord, Any, String](
      ("setup", "schemaEvolution", "givenObj", "expectedObj", "topic"),
      (avroSetup, None, PaymentV2.record, PaymentV2.record, "backward.from-record"),
      (avroSetup, Some(PaymentV1.schema), PaymentV2.record, PaymentV1.record, "backward.from-subject-version"),
      (jsonSetup, None, PaymentV2.record, encode(PaymentV2.exampleData), "json.backward.latest.from-record"),
      (jsonSetup, Some(PaymentV1.schema), PaymentV2.record, encode(PaymentV1.exampleData), "json.backward.latest.from-subject-version")
    )

    runSerializationTest(table, version, schemas)
  }

  test("trying to serialize avro object to wrong type record") {
    val schemas = List(PaymentV1.schema)
    val fromRecordTopic = createAndRegisterTopicConfig("wrong.from-record", schemas)
    val fromSubjectVersionTopic = createAndRegisterTopicConfig("wrong.from-subject-version", schemas)
    val version = None

    val fromRecordSerializer = avroSetup.provider.serializationSchemaFactory.create(fromRecordTopic.output, version, None, kafkaConfig)
    val fromSubjectVersionSerializer = avroSetup.provider.serializationSchemaFactory.create(fromSubjectVersionTopic.output, version, Some(new NkSerializableAvroSchema(PaymentV1.schema)), kafkaConfig)

    pushMessage(fromRecordSerializer, FullNameV1.record, fromRecordTopic.output)
    consumeAndVerifyMessage(fromRecordTopic.output, FullNameV1.record)

    assertThrows[AvroSchemaEvolutionException] {
      pushMessage(fromSubjectVersionSerializer, FullNameV1.record, fromRecordTopic.output)
    }
  }

  private def runSerializationTest(table: TableFor5[SchemaRegistryProviderSetup, Option[Schema], GenericRecord, Any, String], version: Option[Int], schemas: List[Schema]): Assertion =
    forAll(table) { (providerSetup: SchemaRegistryProviderSetup, schemaForWrite: Option[Schema], givenObj: GenericRecord, expectedObj: Any, topic: String) =>
      val topicConfig = createAndRegisterTopicConfig(topic, schemas)
      val serializer = providerSetup.provider.serializationSchemaFactory.create(topicConfig.output, version,
        schemaForWrite.map(new NkSerializableAvroSchema(_)), kafkaConfig)

      providerSetup.pushMessage(serializer, givenObj, topicConfig.output)
      providerSetup.consumeAndVerifyMessage(topicConfig.output, expectedObj)
    }
}
