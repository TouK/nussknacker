package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter.{ConfluentAvroToJsonFormatterFactory, JsonPayloadToJsonFormatterFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.jsonpayload.{ConfluentJsonPayloadSerializerFactory, ConfluentKeyValueKafkaJsonDeserializerFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.{ConfluentAvroSerializationSchemaFactory, ConfluentKeyValueKafkaAvroDeserializationFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaBasedMessagesSerdeProvider, SchemaRegistryError, SchemaRegistryUnsupportedTypeError}
import pl.touk.nussknacker.engine.avro.serialization.{KafkaAvroDeserializationSchemaFactory, KafkaAvroSerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.RecordFormatterFactory

class ConfluentAvroSchemaBasedMessagesSerdeProvider(val serializationSchemaFactory: KafkaAvroSerializationSchemaFactory[AvroSchema],
                                                    val deserializationSchemaFactory: KafkaAvroDeserializationSchemaFactory[AvroSchema],
                                                    val recordFormatterFactory: RecordFormatterFactory) extends SchemaBasedMessagesSerdeProvider[AvroSchema] {
  override def validateSchema(schema: AvroSchema): ValidatedNel[SchemaRegistryError, AvroSchema] = {
    /* kafka-avro-serializer does not support Array at top level
    [https://github.com/confluentinc/schema-registry/issues/1298] */
    if (schema.rawSchema().getType == Schema.Type.ARRAY)
      Invalid(NonEmptyList.of(
        SchemaRegistryUnsupportedTypeError("Unsupported Avro type. Top level Arrays are not supported")))
    else
      Valid(schema)
  }
}

object ConfluentAvroSchemaBasedMessagesSerdeProvider extends Serializable {

  def apply(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory): ConfluentAvroSchemaBasedMessagesSerdeProvider = {
    avroSchemaAvroPayload(schemaRegistryClientFactory)
  }

  def avroSchemaAvroPayload(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory): ConfluentAvroSchemaBasedMessagesSerdeProvider = {
    ConfluentAvroSchemaBasedMessagesSerdeProvider(
      new ConfluentAvroSerializationSchemaFactory(schemaRegistryClientFactory),
      new ConfluentKeyValueKafkaAvroDeserializationFactory(schemaRegistryClientFactory),
      new ConfluentAvroToJsonFormatterFactory(schemaRegistryClientFactory)
    )
  }

  def avroSchemaJsonPayload(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory): ConfluentAvroSchemaBasedMessagesSerdeProvider = {
    ConfluentAvroSchemaBasedMessagesSerdeProvider(
      new ConfluentJsonPayloadSerializerFactory(schemaRegistryClientFactory),
      new ConfluentKeyValueKafkaJsonDeserializerFactory(schemaRegistryClientFactory),
      new JsonPayloadToJsonFormatterFactory
    )
  }

  private def apply(serializationSchemaFactory: KafkaAvroSerializationSchemaFactory[AvroSchema],
                    deserializationSchemaFactory: KafkaAvroDeserializationSchemaFactory[AvroSchema],
                    recordFormatterFactory: RecordFormatterFactory): ConfluentAvroSchemaBasedMessagesSerdeProvider = {
    new ConfluentAvroSchemaBasedMessagesSerdeProvider(
      serializationSchemaFactory,
      deserializationSchemaFactory,
      recordFormatterFactory
    )
  }
}
