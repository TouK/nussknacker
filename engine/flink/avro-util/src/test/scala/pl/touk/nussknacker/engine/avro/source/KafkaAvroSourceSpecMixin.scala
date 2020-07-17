package pl.touk.nussknacker.engine.avro.source

import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import org.apache.avro.Schema
import org.apache.avro.generic.GenericContainer
import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.avro.{AvroUtils, TestSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.avro.encode.{BestEffortAvroEncoder, EncoderPolicy}
import pl.touk.nussknacker.engine.avro.schema.{FullNameV1, FullNameV2}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{CachedConfluentSchemaRegistryClientFactory, MockConfluentSchemaRegistryClientBuilder}
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor

trait KafkaAvroSourceSpecMixin {

  final private val avroEncoder = BestEffortAvroEncoder(EncoderPolicy.strict)

  protected def createOutput(schema: Schema, data: Map[String, Any]): LazyParameter[GenericContainer] = {
    val record = avroEncoder.encodeRecordOrError(data, schema)
    new LazyParameter[GenericContainer] {
      override def returnType: typing.TypingResult = AvroSchemaTypeDefinitionExtractor.typeDefinition(record.getSchema)
    }
  }

  object KafkaAvroSourceMockSchemaRegistry {

    val RecordTopic: String = "testAvroRecordTopic1"
    val IntTopic: String = "testAvroIntTopic1"

    val IntSchema: Schema = AvroUtils.parseSchema(
      """{
        |  "type": "int"
        |}
    """.stripMargin
    )

    val schemaRegistryMockClient: CSchemaRegistryClient = new MockConfluentSchemaRegistryClientBuilder()
      .register(RecordTopic, FullNameV1.schema, 1, isKey = false)
      .register(RecordTopic, FullNameV2.schema, 2, isKey = false)
      .register(IntTopic, IntSchema, 1, isKey = false)
      .register(IntTopic, IntSchema, 1, isKey = true)
      .build

    val factory: CachedConfluentSchemaRegistryClientFactory = TestSchemaRegistryClientFactory(schemaRegistryMockClient)
  }
}
