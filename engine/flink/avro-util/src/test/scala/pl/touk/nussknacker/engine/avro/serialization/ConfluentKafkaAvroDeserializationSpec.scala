import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import org.apache.avro.Schema
import org.apache.avro.generic.{GenericData, GenericRecord}
import org.apache.kafka.common.errors.SerializationException
import org.scalatest.Assertion
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor4}
import pl.touk.nussknacker.engine.avro.KafkaAvroSpecMixin
import pl.touk.nussknacker.engine.avro.schema.{FullNameV1, PaymentV1, PaymentV2}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.{ConfluentKafkaAvroDeserializationSchemaFactory, SchemaDeterminingStrategy}
import pl.touk.nussknacker.engine.avro.serialization.ConfluentKafkaAvroSeDeSpecMixin
import pl.touk.nussknacker.engine.kafka.serialization.KafkaVersionAwareValueDeserializationSchemaFactory

class ConfluentKafkaAvroDeserializationSpec extends KafkaAvroSpecMixin with TableDrivenPropertyChecks with ConfluentKafkaAvroSeDeSpecMixin {

  import MockSchemaRegistry._
  import SchemaDeterminingStrategy._
  import org.apache.flink.api.scala._

  override protected def schemaRegistryClient: CSchemaRegistryClient = schemaRegistryMockClient

  override protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory = factory

  private val fromSubjectVersionFactory = new ConfluentKafkaAvroDeserializationSchemaFactory[GenericData.Record](FromSubjectVersion, factory, false)
  private val fromRecordFactory = new ConfluentKafkaAvroDeserializationSchemaFactory[GenericData.Record](FromRecord, factory, false)

  test("should properly deserialize record to avro object with same schema version") {
    val schemas = List(PaymentV1.schema)
    val version = Some(1)

    val table = Table[KafkaVersionAwareValueDeserializationSchemaFactory[_], GenericRecord, GenericRecord, String](
      ("factory", "givenObj", "expectedObj", "topic"),
      (fromRecordFactory, PaymentV1.record, PaymentV1.record, "simple.from-record"),
      (fromSubjectVersionFactory, PaymentV1.record, PaymentV1.record, "simple.from-subject-version")
    )

    runDeserializationTest(table, version, schemas)
  }

  test("should properly deserialize record to avro object with newer compatible schema version") {
    val schemas = List(PaymentV1.schema, PaymentV2.schema)
    val version = Some(2)

    val table = Table[KafkaVersionAwareValueDeserializationSchemaFactory[_], GenericRecord, GenericRecord, String](
      ("factory", "givenObj", "expectedObj", "topic"),
      (fromRecordFactory, PaymentV1.record, PaymentV1.record, "forward.from-record"),
      (fromSubjectVersionFactory, PaymentV1.record, PaymentV2.record, "forward.from-subject-version")
    )

    runDeserializationTest(table, version, schemas)
  }

  test("should properly deserialize record to avro object with latest compatible schema version") {
    val schemas = List(PaymentV1.schema, PaymentV2.schema)
    val version = None

    val table = Table[KafkaVersionAwareValueDeserializationSchemaFactory[_], GenericRecord, GenericRecord, String](
      ("factory", "givenObj", "expectedObj", "topic"),
      (fromRecordFactory, PaymentV1.record, PaymentV1.record, "forward.latest.from-record"),
      (fromSubjectVersionFactory, PaymentV1.record, PaymentV2.record, "forward.latest.from-subject-version")
    )

    runDeserializationTest(table, version, schemas)
  }

  test("should properly deserialize record to avro object with older compatible schema version") {
    val schemas = List(PaymentV1.schema, PaymentV2.schema)
    val version = Some(1)

    val table = Table[KafkaVersionAwareValueDeserializationSchemaFactory[_], GenericRecord, GenericRecord, String](
      ("factory", "givenObj", "expectedObj", "topic"),
      (fromRecordFactory, PaymentV2.record, PaymentV2.record, "backward.from-record"),
      (fromSubjectVersionFactory, PaymentV2.record, PaymentV1.record, "backward..from-subject-version")
    )

    runDeserializationTest(table, version, schemas)
  }

  test("trying to deserialize record to avro object with wrong type schema") {
    val schemas = List(PaymentV1.schema)
    val fromRecordTopic = createAndRegisterTopicConfig("wrong.from-record", schemas)
    val fromSubjectVersionTopic = createAndRegisterTopicConfig("wrong.from-subject-version", schemas)
    val version = None

    pushMessage(FullNameV1.record, fullNameTopic, Some(fromRecordTopic.input))
    pushMessage(FullNameV1.record, fullNameTopic, Some(fromSubjectVersionTopic.input))

    val fromRecordDeserializer = fromRecordFactory.create(List(fromRecordTopic.input), version, kafkaConfig)
    val fromSubjectVersionDeserializer = fromSubjectVersionFactory.create(List(fromSubjectVersionTopic.input), version, kafkaConfig)

    val result = consumeMessages(fromRecordDeserializer, fromRecordTopic.input)
    result shouldBe List(FullNameV1.record)

    assertThrows[SerializationException] {
      consumeMessages(fromSubjectVersionDeserializer, fromSubjectVersionTopic.input)
    }
  }

  test("trying to deserialize record to avro object with not exists schema version") {
    val schemas = List(PaymentV1.schema)
    val fromRecordTopic = createAndRegisterTopicConfig("not-exist-version.from-record", schemas)
    val fromSubjectVersionTopic = createAndRegisterTopicConfig("not-exist-version.from-subject-version", schemas)
    val version = Some(100)

    pushMessage(PaymentV1.record, fromRecordTopic.input)
    pushMessage(PaymentV1.record, fromSubjectVersionTopic.input)

    val fromRecordDeserializer = fromRecordFactory.create(List(fromRecordTopic.input), version, kafkaConfig)
    val fromSubjectVersionDeserializer = fromSubjectVersionFactory.create(List(fromSubjectVersionTopic.input), version, kafkaConfig)

    val result = consumeMessages(fromRecordDeserializer, fromRecordTopic.input)
    result shouldBe List(PaymentV1.record)

    assertThrows[SerializationException] {
      consumeMessages(fromSubjectVersionDeserializer, fromSubjectVersionTopic.input)
    }
  }

  private def runDeserializationTest(table: TableFor4[KafkaVersionAwareValueDeserializationSchemaFactory[_], GenericRecord, GenericRecord, String], version: Option[Int], schemas: List[Schema]): Assertion =
    forAll(table) { (factory: KafkaVersionAwareValueDeserializationSchemaFactory[_], givenObj: GenericRecord, expectedObj: GenericRecord, topic: String) =>
      val topicConfig = createAndRegisterTopicConfig(topic, schemas)

      pushMessage(givenObj, topicConfig.input)

      val deserializer = factory.create(List(topicConfig.input), version, kafkaConfig)
      val deserializedObject = consumeMessages(deserializer, topicConfig.input)
      deserializedObject shouldBe List(expectedObj)
    }
}
