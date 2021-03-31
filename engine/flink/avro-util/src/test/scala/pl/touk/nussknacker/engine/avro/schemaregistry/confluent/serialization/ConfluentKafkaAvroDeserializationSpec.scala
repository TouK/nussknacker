package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization

import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.errors.SerializationException
import org.scalatest.Assertion
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor5}
import pl.touk.nussknacker.engine.avro.RuntimeSchemaData
import pl.touk.nussknacker.engine.avro.helpers.SchemaRegistryMixin
import pl.touk.nussknacker.engine.avro.schema.{FullNameV1, PaymentV1, PaymentV2}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentUtils

class ConfluentKafkaAvroDeserializationSpec extends SchemaRegistryMixin with TableDrivenPropertyChecks with ConfluentKafkaAvroSeDeSpecMixin {

  import MockSchemaRegistry._

  override protected def schemaRegistryClient: CSchemaRegistryClient = schemaRegistryMockClient

  test("should properly deserialize record to avro object with same schema version") {
    val schemas = List(PaymentV1.schema)
    val version = Some(1)

    val table = Table[SchemaRegistryProviderSetup, Boolean, Any, GenericRecord, String](
      ("setup", "schemaEvolution", "givenObj", "expectedObj", "topic"),
      (avroSetup, false, PaymentV1.record, PaymentV1.record, "simple.from-record"),
      (avroSetup, true, PaymentV1.record, PaymentV1.record, "simple.from-subject-version"),
      (jsonSetup, true, PaymentV1.exampleData, PaymentV1.record, "json.simple.from-subject-version")

    )

    runDeserializationTest(table, version, schemas)
  }

  test("should properly deserialize record to avro object with newer compatible schema version") {
    val schemas = List(PaymentV1.schema, PaymentV2.schema)
    val version = Some(2)

    val table = Table[SchemaRegistryProviderSetup, Boolean, Any, GenericRecord, String](
      ("setup", "schemaEvolution", "givenObj", "expectedObj", "topic"),
      (avroSetup, false, PaymentV1.record, PaymentV1.record, "forward.from-record"),
      (avroSetup, true, PaymentV1.record, PaymentV2.record, "forward.from-subject-version"),
      (jsonSetup, true, PaymentV1.exampleData, PaymentV2.record, "json.forward.from-subject-version")
    )

    runDeserializationTest(table, version, schemas)
  }

  test("should properly deserialize record to avro object with latest compatible schema version") {
    val schemas = List(PaymentV1.schema, PaymentV2.schema)
    val version = None

    val table = Table[SchemaRegistryProviderSetup, Boolean, Any, GenericRecord, String](
      ("setup", "schemaEvolution", "givenObj", "expectedObj", "topic"),
      (avroSetup, false, PaymentV1.record, PaymentV1.record, "forward.latest.from-record"),
      (avroSetup, true, PaymentV1.record, PaymentV2.record, "forward.latest.from-subject-version"),
      (jsonSetup, true, PaymentV1.exampleData, PaymentV2.record, "json.forward.latest.from-subject-version")
    )

    runDeserializationTest(table, version, schemas)
  }

  test("should properly deserialize record to avro object with older compatible schema version") {
    val schemas = List(PaymentV1.schema, PaymentV2.schema)
    val version = Some(1)

    val table = Table[SchemaRegistryProviderSetup, Boolean, Any, GenericRecord, String](
      ("setup", "schemaEvolution", "givenObj", "expectedObj", "topic"),
      (avroSetup, false, PaymentV2.record, PaymentV2.record, "backward.from-record"),
      (avroSetup, true, PaymentV2.record, PaymentV1.record, "backward.from-subject-version"),
      (jsonSetup, true, PaymentV2.exampleData, PaymentV1.record, "json.backward.from-subject-version")
    )

    runDeserializationTest(table, version, schemas)
  }

  test("trying to deserialize record to avro object with wrong type schema") {
    val schemas = List(PaymentV1.schema)
    val fromRecordTopic = createAndRegisterTopicConfig("wrong.from-record", schemas)
    val fromSubjectVersionTopic = createAndRegisterTopicConfig("wrong.from-subject-version", schemas)


    pushMessage(FullNameV1.record, fullNameTopic, Some(fromRecordTopic.input))
    pushMessage(FullNameV1.record, fullNameTopic, Some(fromSubjectVersionTopic.input))

    val fromRecordDeserializer = avroSetup.provider.deserializationSchemaFactory.create(kafkaConfig, None)

    consumeAndVerifyMessages(fromRecordDeserializer, fromRecordTopic.input, List(FullNameV1.record))

    val fromSubjectVersionDeserializer = {
      val subject = ConfluentUtils.topicSubject(fromSubjectVersionTopic.input, fromSubjectVersionTopic.isKey)
      val schemaId = schemaRegistryClient.getId(subject, ConfluentUtils.convertToAvroSchema(PaymentV1.schema))
      val schemaData = RuntimeSchemaData(PaymentV1.schema, Some(schemaId))
      avroSetup.provider.deserializationSchemaFactory.create(kafkaConfig, Some(schemaData))
    }

    assertThrows[SerializationException] {
      consumeMessages(fromSubjectVersionDeserializer, fromSubjectVersionTopic.input, count = 1)
    }
  }

  private def runDeserializationTest(table: TableFor5[SchemaRegistryProviderSetup, Boolean, Any, GenericRecord, String], version: Option[Int], schemas: List[Schema]): Assertion = {

    forAll(table) { (setup: SchemaRegistryProviderSetup, schemaEvolution: Boolean, givenObj: Any, expectedObj: GenericRecord, topic: String) =>
      val topicConfig = createAndRegisterTopicConfig(topic, schemas)

      val schemaDataOpt = if (schemaEvolution) {
        val inputSubject = ConfluentUtils.topicSubject(topicConfig.input, topicConfig.isKey)
        val inputSchemaId = schemaRegistryClient.getId(inputSubject, ConfluentUtils.convertToAvroSchema(expectedObj.getSchema))
        Option(RuntimeSchemaData(expectedObj.getSchema, Some(inputSchemaId)))
      } else {
        None
      }
      val deserializer = setup.provider.deserializationSchemaFactory.create(kafkaConfig, schemaDataOpt)

      setup.pushMessage(givenObj, topicConfig.input)

      setup.consumeAndVerifyMessages(deserializer, topicConfig.input, List(expectedObj))
    }

  }

}
