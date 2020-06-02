import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import org.apache.avro.Schema
import org.apache.avro.generic.{GenericData, GenericRecord}
import org.apache.kafka.common.errors.SerializationException
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor5}
import pl.touk.nussknacker.engine.avro.schema.{FullNameV1, PaymentV1, PaymentV2}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{CachedConfluentSchemaRegistryClientFactory, MockConfluentSchemaRegistryClientBuilder}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.{ConfluentKafkaAvroDeserializationSchemaFactory, SchemaDeterminingStrategy}
import pl.touk.nussknacker.engine.avro.{KafkaAvroSpec, TestSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.kafka.serialization.KafkaVersionAwareValueDeserializationSchemaFactory

class ConfluentKafkaAvroDeserializationSpec extends KafkaAvroSpec with TableDrivenPropertyChecks {

  import MockSchemaRegistry._
  import SchemaDeterminingStrategy._
  import org.apache.flink.api.scala._

  override protected def schemaRegistryClient: CSchemaRegistryClient = schemaRegistryMockClient

  private val fromSubjectVersionFactory = new ConfluentKafkaAvroDeserializationSchemaFactory[GenericData.Record](FromSubjectVersion, factory, false)
  private val fromRecordFactory = new ConfluentKafkaAvroDeserializationSchemaFactory[GenericData.Record](FromRecord, factory, false)

  test("should properly deserialize generated record in v1 with version set to v1") {
    val schemas = List(PaymentV1.schema)

    val table = Table[KafkaVersionAwareValueDeserializationSchemaFactory[_], GenericRecord, GenericRecord, String, Option[Int]](
      ("factory", "givenObj", "expectedObj", "topic", "version"),
      (fromRecordFactory, PaymentV1.record, PaymentV1.record, "simple.from-record", Some(1)),
      (fromSubjectVersionFactory, PaymentV1.record, PaymentV1.record, "simple.from-subject-version", Some(1))
    )

    runDeserializationTest(table, schemas)
  }

  test("should properly deserialize generated record in v1 with version set to v2") {
    val schemas = List(PaymentV1.schema, PaymentV2.schema)

    val table = Table[KafkaVersionAwareValueDeserializationSchemaFactory[_], GenericRecord, GenericRecord, String, Option[Int]](
      ("factory", "givenObj", "expectedObj", "topic", "version"),
      (fromRecordFactory, PaymentV1.record, PaymentV1.record, "forward.from-record", Some(1)),
      (fromSubjectVersionFactory, PaymentV1.record, PaymentV2.record, "forward.from-subject-version", Some(2))
    )

    runDeserializationTest(table, schemas)
  }

  test("should properly deserialize generated record in v1 with version set to latest") {
    val schemas = List(PaymentV1.schema, PaymentV2.schema)

    val table = Table[KafkaVersionAwareValueDeserializationSchemaFactory[_], GenericRecord, GenericRecord, String, Option[Int]](
      ("factory", "givenObj", "expectedObj", "topic", "version"),
      (fromRecordFactory, PaymentV1.record, PaymentV1.record, "forward.latest.from-record", None),
      (fromSubjectVersionFactory, PaymentV1.record, PaymentV2.record, "forward.latest.from-subject-version", None)
    )

    runDeserializationTest(table, schemas)
  }

  test("should properly deserialize generated record in v2 with version set to v1") {
    val schemas = List(PaymentV1.schema, PaymentV2.schema)

    val table = Table[KafkaVersionAwareValueDeserializationSchemaFactory[_], GenericRecord, GenericRecord, String, Option[Int]](
      ("factory", "givenObj", "expectedObj", "topic", "version"),
      (fromRecordFactory, PaymentV2.record, PaymentV2.record, "backward.from-record", Some(1)),
      (fromSubjectVersionFactory, PaymentV2.record, PaymentV1.record, "backward..from-subject-version", Some(1))
    )

    runDeserializationTest(table, schemas)
  }

  test("should properly deserialize generated record in v2 with version set to latest") {
    val schemas = List(PaymentV1.schema, PaymentV2.schema)

    val table = Table[KafkaVersionAwareValueDeserializationSchemaFactory[_], GenericRecord, GenericRecord, String, Option[Int]](
      ("factory", "givenObj", "expectedObj", "topic", "version"),
      (fromRecordFactory, PaymentV2.record, PaymentV2.record, "backward.latest.from-record", None),
      (fromSubjectVersionFactory, PaymentV2.record, PaymentV2.record, "backward.latest.from-subject-version", None)
    )

    runDeserializationTest(table, schemas)
  }

  test("trying to deserialize generated wrong type record") {
    val schemas = List(PaymentV1.schema, PaymentV2.schema)
    val fromRecordTopic = createAndRegisterTopicConfig("wrong.from-record", schemas)
    val fromSubjectVersionTopic = createAndRegisterTopicConfig("wrong.from-subject-version", schemas)

    pushMessage(FullNameV1.record, fullNameTopic, Some(fromRecordTopic.input))
    pushMessage(FullNameV1.record, fullNameTopic, Some(fromSubjectVersionTopic.input))

    val fromRecordDeserializer = fromRecordFactory.create(List(fromRecordTopic.input), None, kafkaConfig)
    val fromSubjectVersionDeserializer = fromSubjectVersionFactory.create(List(fromSubjectVersionTopic.input), None, kafkaConfig)

    val result = consumeLastMessage(fromRecordDeserializer, fromRecordTopic.input)
    result shouldBe List(FullNameV1.record)

    assertThrows[SerializationException] {
      consumeLastMessage(fromSubjectVersionDeserializer, fromSubjectVersionTopic.input)
    }
  }

  test("trying to deserialize generated record to schema with not exists version") {
    val schemas = List(PaymentV1.schema, PaymentV2.schema)
    val fromRecordTopic = createAndRegisterTopicConfig("not-exist-version.from-record", schemas)
    val fromSubjectVersionTopic = createAndRegisterTopicConfig("not-exist-version.from-subject-version", schemas)

    pushMessage(PaymentV1.record, fromRecordTopic.input)
    pushMessage(PaymentV1.record, fromSubjectVersionTopic.input)

    val fromRecordDeserializer = fromRecordFactory.create(List(fromRecordTopic.input), Some(100), kafkaConfig)
    val fromSubjectVersionDeserializer = fromSubjectVersionFactory.create(List(fromSubjectVersionTopic.input), Some(100), kafkaConfig)

    val result = consumeLastMessage(fromRecordDeserializer, fromRecordTopic.input)
    result shouldBe List(PaymentV1.record)

    assertThrows[SerializationException] {
      consumeLastMessage(fromSubjectVersionDeserializer, fromSubjectVersionTopic.input)
    }
  }

  private def runDeserializationTest(table: TableFor5[KafkaVersionAwareValueDeserializationSchemaFactory[_], GenericRecord, GenericRecord, String, Option[Int]], schemas: List[Schema]) =
    forAll(table) { (factory: KafkaVersionAwareValueDeserializationSchemaFactory[_], givenObj: GenericRecord, expectedObj: GenericRecord, topic: String, version: Option[Int]) =>
      val topicConfig = createAndRegisterTopicConfig(topic, schemas)

      pushMessage(givenObj, topicConfig.input)

      val deserializer = factory.create(List(topicConfig.input), version, kafkaConfig)
      val deserializedObject = consumeLastMessage(deserializer, topicConfig.input)
      deserializedObject shouldBe List(expectedObj)
    }

  object MockSchemaRegistry {
    final val fullNameTopic = "full-name"

    val schemaRegistryMockClient: CSchemaRegistryClient =  new MockConfluentSchemaRegistryClientBuilder()
      .register(fullNameTopic, FullNameV1.schema, 1, isKey = false)
      .build

    val factory: CachedConfluentSchemaRegistryClientFactory = TestSchemaRegistryClientFactory(schemaRegistryMockClient)
  }
}
