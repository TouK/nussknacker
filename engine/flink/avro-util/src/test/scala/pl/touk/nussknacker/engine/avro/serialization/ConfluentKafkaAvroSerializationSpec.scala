package pl.touk.nussknacker.engine.avro.serialization

import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.apache.flink.formats.avro.typeutils.NkSerializableAvroSchema
import org.apache.kafka.common.errors.SerializationException
import org.scalatest.Assertion
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor4}
import pl.touk.nussknacker.engine.avro.KafkaAvroSpecMixin
import pl.touk.nussknacker.engine.avro.schema.{FullNameV1, PaymentV1, PaymentV2}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.ConfluentAvroSerializationSchemaFactory
import pl.touk.nussknacker.engine.avro.typed.AvroSettings

class ConfluentKafkaAvroSerializationSpec extends KafkaAvroSpecMixin with TableDrivenPropertyChecks with ConfluentKafkaAvroSeDeSpecMixin {

  import MockSchemaRegistry._

  override protected def schemaRegistryClient: CSchemaRegistryClient = schemaRegistryMockClient

  override protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory = factory

  private val confluentSerializationSchemaFactory = new ConfluentAvroSerializationSchemaFactory(factory, AvroSettings.default)

  test("should properly serialize avro object to record with same schema version") {
    val schemas = List(PaymentV1.schema)
    val version = Some(1)

    val table = Table[Boolean, GenericRecord, GenericRecord, String](
      ("schemaEvolution", "givenObj", "expectedObj", "topic"),
      (false, PaymentV1.record, PaymentV1.record, "simple.from-record"),
      (true, PaymentV1.record, PaymentV1.record, "simple.from-subject-version")
    )

    runSerializationTest(table, version, schemas)
  }

  test("should properly serialize avro object to record with newer compatible schema version") {
    val schemas = List(PaymentV1.schema, PaymentV2.schema)
    val version = Some(2)

    val table = Table[Boolean, GenericRecord, GenericRecord, String](
      ("schemaEvolution", "givenObj", "expectedObj", "topic"),
      (false, PaymentV1.record, PaymentV1.record, "forward.from-record"),
      (true, PaymentV1.record, PaymentV2.record, "forward.from-subject-version")
    )

    runSerializationTest(table, version, schemas)
  }

  test("should properly serialize avro object to record with latest compatible schema version") {
    val schemas = List(PaymentV1.schema, PaymentV2.schema)
    val version = None

    val table = Table[Boolean, GenericRecord, GenericRecord, String](
      ("schemaEvolution", "givenObj", "expectedObj", "topic"),
      (false, PaymentV1.record, PaymentV1.record, "forward.latest.from-record"),
      (true, PaymentV1.record, PaymentV2.record, "forward.latest.from-subject-version")
    )

    runSerializationTest(table, version, schemas)
  }

  test("should properly serialize avro object to record with older compatible schema version") {
    val schemas = List(PaymentV1.schema, PaymentV2.schema)
    val version = Some(1)

    val table = Table[Boolean, GenericRecord, GenericRecord, String](
      ("schemaEvolution", "givenObj", "expectedObj", "topic"),
      (false, PaymentV2.record, PaymentV2.record, "backward.from-record"),
      (true, PaymentV2.record, PaymentV1.record, "backward..from-subject-version")
    )

    runSerializationTest(table, version, schemas)
  }

  test("trying to serialize avro object to wrong type record") {
    val schemas = List(PaymentV1.schema)
    val fromRecordTopic = createAndRegisterTopicConfig("wrong.from-record", schemas)
    val fromSubjectVersionTopic = createAndRegisterTopicConfig("wrong.from-subject-version", schemas)
    val version = None

    val fromRecordSerializer = confluentSerializationSchemaFactory.create(fromRecordTopic.output, version, None, kafkaConfig)
    val fromSubjectVersionSerializer = confluentSerializationSchemaFactory.create(fromSubjectVersionTopic.output, version, Some(new NkSerializableAvroSchema(PaymentV1.schema)), kafkaConfig)

    pushMessage(fromRecordSerializer, FullNameV1.record, fromRecordTopic.output)
    consumeAndVerifyMessage(fromRecordTopic.output, FullNameV1.record)

    assertThrows[SerializationException] {
      pushMessage(fromSubjectVersionSerializer, FullNameV1.record, fromRecordTopic.output)
    }
  }

  private def runSerializationTest(table: TableFor4[Boolean, GenericRecord, GenericRecord, String], version: Option[Int], schemas: List[Schema]): Assertion =
    forAll(table) { (schemaEvolution: Boolean, givenObj: GenericRecord, expectedObj: GenericRecord, topic: String) =>
      val topicConfig = createAndRegisterTopicConfig(topic, schemas)
      val serializer = confluentSerializationSchemaFactory.create(topicConfig.output, version, if (schemaEvolution) Some(new NkSerializableAvroSchema(expectedObj.getSchema)) else None, kafkaConfig)

      pushMessage(serializer, givenObj, topicConfig.output)
      consumeAndVerifyMessage(topicConfig.output, expectedObj)
    }
}
