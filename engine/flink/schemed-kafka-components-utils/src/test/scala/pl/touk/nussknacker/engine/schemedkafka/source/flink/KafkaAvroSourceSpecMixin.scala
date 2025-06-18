package pl.touk.nussknacker.engine.schemedkafka.source.flink

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils
import pl.touk.nussknacker.engine.schemedkafka.schema._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.MockConfluentSchemaRegistryClientBuilder
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.MockSchemaRegistryClientFactory

trait KafkaAvroSourceSpecMixin {

  object KafkaAvroSourceMockSchemaRegistry {

    val RecordTopic          = UnspecializedTopicName("testAvroRecordTopic1")
    val RecordTopicWithKey   = UnspecializedTopicName("testAvroRecordTopic1WithKey")
    val IntTopicWithKey      = UnspecializedTopicName("testAvroIntTopic1WithKey")
    val IntTopicNoKey        = UnspecializedTopicName("testAvroIntTopic1NoKey")
    val ArrayOfNumbersTopic  = UnspecializedTopicName("testArrayOfNumbersTopic")
    val ArrayOfRecordsTopic  = UnspecializedTopicName("testArrayOfRecordsTopic")
    val InvalidDefaultsTopic = UnspecializedTopicName("testAvroInvalidDefaultsTopic1")
    val PaymentDateTopic     = UnspecializedTopicName("testPaymentDateTopic")

    val IntSchema: AvroSchema = toAvroSchema(
      AvroUtils.parseSchema(
        """{
        |  "type": "int"
        |}
        """.stripMargin
      )
    )

    val ArrayOfIntsSchema: AvroSchema = arraySchema("\"int\"")

    val ArrayOfLongsSchema: AvroSchema = arraySchema("\"long\"")

    val ArrayOfRecordsV1Schema: AvroSchema = arraySchema(FullNameV1.schema.toString)

    val ArrayOfRecordsV2Schema: AvroSchema = arraySchema(FullNameV2.schema.toString)

    private def arraySchema(itemsType: String) = toAvroSchema(
      AvroUtils.parseSchema(
        s"""{
         |  "type": "array",
         |  "items": $itemsType
         |}
         """.stripMargin
      )
    )

    val InvalidDefaultsSchema: AvroSchema = toAvroSchema(
      AvroUtils.nonRestrictiveParseSchema(
        """{
        |  "type": "record",
        |  "name": "invalid",
        |  "namespace": "com.test",
        |  "fields": [
        |    {
        |      "name": "field1",
        |      "type": "string",
        |      "default": null
        |    }
        |  ]
        |}
    """.stripMargin
      )
    )

    private def toAvroSchema(schema: Schema): AvroSchema = ConfluentUtils.convertToAvroSchema(schema)

    // ALL schemas, for Generic and Specific records, must be regitered in schema registry
    val schemaRegistryMockClient: CSchemaRegistryClient = new MockConfluentSchemaRegistryClientBuilder()
      .register(RecordTopic.name, FullNameV1.confluentSchema, 1, isKey = false)
      .register(RecordTopic.name, FullNameV2.confluentSchema, 2, isKey = false)
      .register(RecordTopicWithKey.name, PaymentV1.confluentSchema, 1, isKey = false)
      .register(RecordTopicWithKey.name, FullNameV1.confluentSchema, 1, isKey = true)
      .register(IntTopicNoKey.name, IntSchema, 1, isKey = false)
      .register(IntTopicWithKey.name, IntSchema, 1, isKey = false)
      .register(IntTopicWithKey.name, IntSchema, 1, isKey = true)
      .register(InvalidDefaultsTopic.name, InvalidDefaultsSchema, 1, isKey = false)
      .register(ArrayOfNumbersTopic.name, ArrayOfIntsSchema, 1, isKey = false)
      .register(ArrayOfNumbersTopic.name, ArrayOfLongsSchema, 2, isKey = false)
      .register(ArrayOfRecordsTopic.name, ArrayOfRecordsV1Schema, 1, isKey = false)
      .register(ArrayOfRecordsTopic.name, ArrayOfRecordsV2Schema, 2, isKey = false)
      .register(PaymentDateTopic.name, PaymentDate.confluentSchema, 1, isKey = false)
      .build

    val factory: SchemaRegistryClientFactory = MockSchemaRegistryClientFactory.confluentBased(schemaRegistryMockClient)
  }

}
