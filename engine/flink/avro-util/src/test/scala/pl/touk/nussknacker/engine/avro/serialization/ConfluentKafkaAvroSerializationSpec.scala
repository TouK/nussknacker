package pl.touk.nussknacker.engine.avro.serialization

import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.errors.SerializationException
import org.scalatest.Assertion
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor4}
import pl.touk.nussknacker.engine.avro.KafkaAvroSpecMixin
import pl.touk.nussknacker.engine.avro.schema.{FullNameV1, PaymentV1, PaymentV2}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.{ConfluentAvroSerializationSchemaFactory, SchemaDeterminingStrategy}
import pl.touk.nussknacker.engine.kafka.serialization.KafkaVersionAwareValueSerializationSchemaFactory

class ConfluentKafkaAvroSerializationSpec extends KafkaAvroSpecMixin with TableDrivenPropertyChecks with ConfluentKafkaAvroSeDeSpecMixin {

  import MockSchemaRegistry._
  import SchemaDeterminingStrategy._

  override protected def schemaRegistryClient: CSchemaRegistryClient = schemaRegistryMockClient

  override protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory = factory

  private val fromSubjectVersionFactory = new ConfluentAvroSerializationSchemaFactory(FromSubjectVersion, factory)
  private val fromRecordFactory = new ConfluentAvroSerializationSchemaFactory(FromRecord, factory)

  test("should properly serialize avro object to record with same schema version") {
    val schemas = List(PaymentV1.schema)
    val version = Some(1)

    val table = Table[KafkaVersionAwareValueSerializationSchemaFactory[Any], GenericRecord, GenericRecord, String](
      ("factory", "givenObj", "expectedObj", "topic"),
      (fromRecordFactory, PaymentV1.record, PaymentV1.record, "simple.from-record"),
      (fromSubjectVersionFactory, PaymentV1.record, PaymentV1.record, "simple.from-subject-version")
    )

    runSerializationTest(table, version, schemas)
  }

  test("should properly serialize avro object to record with newer compatible schema version") {
    val schemas = List(PaymentV1.schema, PaymentV2.schema)
    val version = Some(2)

    val table = Table[KafkaVersionAwareValueSerializationSchemaFactory[Any], GenericRecord, GenericRecord, String](
      ("factory", "givenObj", "expectedObj", "topic"),
      (fromRecordFactory, PaymentV1.record, PaymentV1.record, "forward.from-record"),
      (fromSubjectVersionFactory, PaymentV1.record, PaymentV2.record, "forward.from-subject-version")
    )

    runSerializationTest(table, version, schemas)
  }

  test("should properly serialize avro object to record with latest compatible schema version") {
    val schemas = List(PaymentV1.schema, PaymentV2.schema)
    val version = None

    val table = Table[KafkaVersionAwareValueSerializationSchemaFactory[Any], GenericRecord, GenericRecord, String](
      ("factory", "givenObj", "expectedObj", "topic"),
      (fromRecordFactory, PaymentV1.record, PaymentV1.record, "forward.latest.from-record"),
      (fromSubjectVersionFactory, PaymentV1.record, PaymentV2.record, "forward.latest.from-subject-version")
    )

    runSerializationTest(table, version, schemas)
  }

  test("should properly serialize avro object to record with older compatible schema version") {
    val schemas = List(PaymentV1.schema, PaymentV2.schema)
    val version = Some(1)

    val table = Table[KafkaVersionAwareValueSerializationSchemaFactory[Any], GenericRecord, GenericRecord, String](
      ("factory", "givenObj", "expectedObj", "topic"),
      (fromRecordFactory, PaymentV2.record, PaymentV2.record, "backward.from-record"),
      (fromSubjectVersionFactory, PaymentV2.record, PaymentV1.record, "backward..from-subject-version")
    )

    runSerializationTest(table, version, schemas)
  }

  test("trying to serialize avro object to wrong type record") {
    val schemas = List(PaymentV1.schema)
    val fromRecordTopic = createAndRegisterTopicConfig("wrong.from-record", schemas)
    val fromSubjectVersionTopic = createAndRegisterTopicConfig("wrong.from-subject-version", schemas)
    val version = None

    val fromRecordSerializer = fromRecordFactory.create(fromRecordTopic.output, version, kafkaConfig)
    val fromSubjectVersionSerializer = fromSubjectVersionFactory.create(fromSubjectVersionTopic.output, version, kafkaConfig)

    pushMessage(fromRecordSerializer, FullNameV1.record, fromRecordTopic.output)
    consumeAndVerifyMessages(fromRecordTopic.output, FullNameV1.record)

    assertThrows[SerializationException] {
      pushMessage(fromSubjectVersionSerializer, FullNameV1.record, fromRecordTopic.output)
    }
  }

  test("trying to serialize avro object to record with not exists schema version") {
    val schemas = List(PaymentV1.schema)
    val fromRecordTopic = createAndRegisterTopicConfig("not-exist-version.from-record", schemas)
    val fromSubjectVersionTopic = createAndRegisterTopicConfig("not-exist-version.from-subject-version", schemas)
    val version = Some(100)

    val fromRecordSerializer = fromRecordFactory.create(fromRecordTopic.output, version, kafkaConfig)
    val fromSubjectVersionSerializer = fromSubjectVersionFactory.create(fromSubjectVersionTopic.output, version, kafkaConfig)

    pushMessage(fromRecordSerializer, PaymentV1.record, fromRecordTopic.output)
    consumeAndVerifyMessages(fromRecordTopic.output, PaymentV1.record)

    assertThrows[SerializationException] {
      pushMessage(fromSubjectVersionSerializer, PaymentV1.record, fromRecordTopic.output)
    }
  }

  private def runSerializationTest(table: TableFor4[KafkaVersionAwareValueSerializationSchemaFactory[Any], GenericRecord, GenericRecord, String], version: Option[Int], schemas: List[Schema]): Assertion =
    forAll(table) { (factory: KafkaVersionAwareValueSerializationSchemaFactory[Any], givenObj: GenericRecord, expectedObj: GenericRecord, topic: String) =>
      val topicConfig = createAndRegisterTopicConfig(topic, schemas)
      val serializer = factory.create(topicConfig.output, version, kafkaConfig)

      pushMessage(serializer, givenObj, topicConfig.output)
      consumeAndVerifyMessages(topicConfig.output, expectedObj)
    }
}
