package pl.touk.nussknacker.engine.avro.source.flink

import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import org.apache.avro.Schema
import org.apache.avro.generic.GenericContainer
import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.avro.encode.{BestEffortAvroEncoder, ValidationMode}
import pl.touk.nussknacker.engine.avro.schema._
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{CachedConfluentSchemaRegistryClientFactory, MockConfluentSchemaRegistryClientBuilder}
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.avro.{AvroUtils, TestSchemaRegistryClientFactory}

trait KafkaAvroSourceSpecMixin {

  final private val avroEncoder = BestEffortAvroEncoder(ValidationMode.strict)

  protected def createOutput(schema: Schema, data: Map[String, Any]): LazyParameter[GenericContainer] = {
    val record = avroEncoder.encodeRecordOrError(data, schema)
    new LazyParameter[GenericContainer] {
      override def returnType: typing.TypingResult = AvroSchemaTypeDefinitionExtractor.typeDefinition(record.getSchema)
    }
  }

  object KafkaAvroSourceMockSchemaRegistry {

    val RecordTopic: String = "testAvroRecordTopic1"
    val RecordTopicWithKey: String = "testAvroRecordTopic1WithKey"
    val IntTopicWithKey: String = "testAvroIntTopic1WithKey"
    val IntTopicNoKey: String = "testAvroIntTopic1NoKey"
    val InvalidDefaultsTopic: String = "testAvroInvalidDefaultsTopic1"
    val PaymentDateTopic: String = "testPaymentDateTopic"
    val GeneratedWithLogicalTypesTopic: String = "testGeneratedWithLogicalTypesTopic"

    val IntSchema: Schema = AvroUtils.parseSchema(
      """{
        |  "type": "int"
        |}
    """.stripMargin
    )

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
      .register(PaymentDateTopic, PaymentDate.schema, 1, isKey = false)
      .register(GeneratedWithLogicalTypesTopic, GeneratedAvroClassWithLogicalTypes.getClassSchema, 1, isKey = false)
      .build

    val factory: CachedConfluentSchemaRegistryClientFactory = TestSchemaRegistryClientFactory(schemaRegistryMockClient)
  }
}
