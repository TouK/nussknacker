package pl.touk.nussknacker.engine.avro.serialization

import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.errors.SerializationException
import org.scalatest.Assertion
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor4}
import pl.touk.nussknacker.engine.avro.schema.{FullNameV1, PaymentV1, PaymentV2}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.ConfluentKafkaAvroDeserializationSchemaFactory
import pl.touk.nussknacker.engine.avro.{KafkaAvroSpecMixin, RuntimeSchemaData}

class ConfluentKafkaAvroDeserializationSpec extends KafkaAvroSpecMixin with TableDrivenPropertyChecks with ConfluentKafkaAvroSeDeSpecMixin {

  import MockSchemaRegistry._

  override protected def schemaRegistryClient: CSchemaRegistryClient = schemaRegistryMockClient

  override protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory = factory

  private val confluentDeserializationSchemaFactory = new ConfluentKafkaAvroDeserializationSchemaFactory(factory)

  test("should properly deserialize record to avro object with same schema version") {
    val schemas = List(PaymentV1.schema)
    val version = Some(1)

    val table = Table[Boolean, GenericRecord, GenericRecord, String](
      ("schemaEvolution", "givenObj", "expectedObj", "topic"),
      (false, PaymentV1.record, PaymentV1.record, "simple.from-record"),
      (true, PaymentV1.record, PaymentV1.record, "simple.from-subject-version")
    )

    runDeserializationTest(table, version, schemas)
  }

  test("should properly deserialize record to avro object with newer compatible schema version") {
    val schemas = List(PaymentV1.schema, PaymentV2.schema)
    val version = Some(2)

    val table = Table[Boolean, GenericRecord, GenericRecord, String](
      ("schemaEvolution", "givenObj", "expectedObj", "topic"),
      (false, PaymentV1.record, PaymentV1.record, "forward.from-record"),
      (true, PaymentV1.record, PaymentV2.record, "forward.from-subject-version")
    )

    runDeserializationTest(table, version, schemas)
  }

  test("should properly deserialize record to avro object with latest compatible schema version") {
    val schemas = List(PaymentV1.schema, PaymentV2.schema)
    val version = None

    val table = Table[Boolean, GenericRecord, GenericRecord, String](
      ("schemaEvolution", "givenObj", "expectedObj", "topic"),
      (false, PaymentV1.record, PaymentV1.record, "forward.latest.from-record"),
      (true, PaymentV1.record, PaymentV2.record, "forward.latest.from-subject-version")
    )

    runDeserializationTest(table, version, schemas)
  }

  test("should properly deserialize record to avro object with older compatible schema version") {
    val schemas = List(PaymentV1.schema, PaymentV2.schema)
    val version = Some(1)

    val table = Table[Boolean, GenericRecord, GenericRecord, String](
      ("schemaEvolution", "givenObj", "expectedObj", "topic"),
      (false, PaymentV2.record, PaymentV2.record, "backward.from-record"),
      (true, PaymentV2.record, PaymentV1.record, "backward..from-subject-version")
    )

    runDeserializationTest(table, version, schemas)
  }

  test("trying to deserialize record to avro object with wrong type schema") {
    val schemas = List(PaymentV1.schema)
    val fromRecordTopic = createAndRegisterTopicConfig("wrong.from-record", schemas)
    val fromSubjectVersionTopic = createAndRegisterTopicConfig("wrong.from-subject-version", schemas)

    pushMessage(FullNameV1.record, fullNameTopic, Some(fromRecordTopic.input))
    pushMessage(FullNameV1.record, fullNameTopic, Some(fromSubjectVersionTopic.input))

    val fromRecordDeserializer = confluentDeserializationSchemaFactory.create(None, kafkaConfig)

    consumeAndVerifyMessages(fromRecordDeserializer, fromRecordTopic.input, List(FullNameV1.record))

    val fromSubjectVersionDeserializer = {
      val subject = ConfluentUtils.topicSubject(fromSubjectVersionTopic.input, fromSubjectVersionTopic.isKey)
      val schemaId = schemaRegistryClient.getId(subject, ConfluentUtils.convertToAvroSchema(PaymentV1.schema))
      val schemaData = RuntimeSchemaData(PaymentV1.schema, Some(schemaId))
      confluentDeserializationSchemaFactory.create(Some(schemaData), kafkaConfig)
    }

    assertThrows[SerializationException] {
      consumeMessages(fromSubjectVersionDeserializer, fromSubjectVersionTopic.input, count = 1)
    }
  }

  private def runDeserializationTest(table: TableFor4[Boolean, GenericRecord, GenericRecord, String], version: Option[Int], schemas: List[Schema]): Assertion =
    forAll(table) { (schemaEvolution: Boolean, givenObj: GenericRecord, expectedObj: GenericRecord, topic: String) =>
      val topicConfig = createAndRegisterTopicConfig(topic, schemas)

      val schemaDataOpt = if (schemaEvolution) {
        val inputSubject = ConfluentUtils.topicSubject(topicConfig.input, topicConfig.isKey)
        val inputSchemaId = schemaRegistryClient.getId(inputSubject, ConfluentUtils.convertToAvroSchema(expectedObj.getSchema))
        Option(RuntimeSchemaData(expectedObj.getSchema, Some(inputSchemaId)))
      } else {
        None
      }
      val deserializer = confluentDeserializationSchemaFactory.create(schemaDataOpt, kafkaConfig)

      pushMessage(givenObj, topicConfig.input)

      consumeAndVerifyMessages(deserializer, topicConfig.input, List(expectedObj))
    }
}
