package pl.touk.nussknacker.engine.schemedkafka.source.flink

import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils
import pl.touk.nussknacker.engine.schemedkafka.schema._
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.MockConfluentSchemaRegistryClientBuilder
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.MockSchemaRegistryClientFactory

trait KafkaAvroSourceSpecMixin {

  object KafkaAvroSourceMockSchemaRegistry {

    val RecordTopic: String          = "testAvroRecordTopic1"
    val RecordTopicWithKey: String   = "testAvroRecordTopic1WithKey"
    val IntTopicWithKey: String      = "testAvroIntTopic1WithKey"
    val IntTopicNoKey: String        = "testAvroIntTopic1NoKey"
    val ArrayOfNumbersTopic: String  = "testArrayOfNumbersTopic"
    val ArrayOfRecordsTopic: String  = "testArrayOfRecordsTopic"
    val InvalidDefaultsTopic: String = "testAvroInvalidDefaultsTopic1"
    val PaymentDateTopic: String     = "testPaymentDateTopic"

    val IntSchema: Schema = AvroUtils.parseSchema(
      """{
        |  "type": "int"
        |}
    """.stripMargin
    )

    val ArrayOfIntsSchema: Schema = arraySchema("\"int\"")

    val ArrayOfLongsSchema: Schema = arraySchema("\"long\"")

    val ArrayOfRecordsV1Schema: Schema = arraySchema(FullNameV1.schema.toString)

    val ArrayOfRecordsV2Schema: Schema = arraySchema(FullNameV2.schema.toString)

    private def arraySchema(itemsType: String) = AvroUtils.parseSchema(s"""{
         |  "type": "array",
         |  "items": $itemsType
         |}
       """.stripMargin)

    val InvalidDefaultsSchema: Schema = AvroUtils.nonRestrictiveParseSchema(
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

    // ALL schemas, for Generic and Specific records, must be regitered in schema registry
    val schemaRegistryMockClient: CSchemaRegistryClient = new MockConfluentSchemaRegistryClientBuilder()
      .register(RecordTopic, FullNameV1.schema, 1, isKey = false)
      .register(RecordTopic, FullNameV2.schema, 2, isKey = false)
      .register(RecordTopicWithKey, PaymentV1.schema, 1, isKey = false)
      .register(RecordTopicWithKey, FullNameV1.schema, 1, isKey = true)
      .register(IntTopicNoKey, IntSchema, 1, isKey = false)
      .register(IntTopicWithKey, IntSchema, 1, isKey = false)
      .register(IntTopicWithKey, IntSchema, 1, isKey = true)
      .register(InvalidDefaultsTopic, InvalidDefaultsSchema, 1, isKey = false)
      .register(ArrayOfNumbersTopic, ArrayOfIntsSchema, 1, isKey = false)
      .register(ArrayOfNumbersTopic, ArrayOfLongsSchema, 2, isKey = false)
      .register(ArrayOfRecordsTopic, ArrayOfRecordsV1Schema, 1, isKey = false)
      .register(ArrayOfRecordsTopic, ArrayOfRecordsV2Schema, 2, isKey = false)
      .register(PaymentDateTopic, PaymentDate.schema, 1, isKey = false)
      .build

    val factory: SchemaRegistryClientFactory = MockSchemaRegistryClientFactory.confluentBased(schemaRegistryMockClient)
  }

}
