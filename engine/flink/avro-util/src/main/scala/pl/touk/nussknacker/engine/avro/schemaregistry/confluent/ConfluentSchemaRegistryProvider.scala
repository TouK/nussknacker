package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{CachedConfluentSchemaRegistryClientFactory, ConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter.ConfluentAvroToJsonFormatterFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.jsonpayload.{ConfluentJsonPayloadDeserializerFactory, ConfluentJsonPayloadSerializerFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.{ConfluentAvroSerializationSchemaFactory, ConfluentKafkaAvroDeserializationSchemaFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaRegistryError, SchemaRegistryProvider, SchemaRegistryUnsupportedTypeError}
import pl.touk.nussknacker.engine.avro.serialization.{KafkaAvroDeserializationSchemaFactory, KafkaAvroSerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.RecordFormatterFactory

class ConfluentSchemaRegistryProvider(val schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                      val serializationSchemaFactory: KafkaAvroSerializationSchemaFactory,
                                      val deserializationSchemaFactory: KafkaAvroDeserializationSchemaFactory,
                                      formatKey: Boolean) extends SchemaRegistryProvider {

  override def recordFormatterFactory: RecordFormatterFactory =
    new ConfluentAvroToJsonFormatterFactory(schemaRegistryClientFactory, formatKey)

  override def validateSchema(schema: Schema): ValidatedNel[SchemaRegistryError, Schema] =
  /* kafka-avro-serializer does not support Array at top level
  [https://github.com/confluentinc/schema-registry/issues/1298] */
    if (schema.getType == Schema.Type.ARRAY)
      Invalid(NonEmptyList.of(
        SchemaRegistryUnsupportedTypeError("Unsupported Avro type. Top level Arrays are not supported")))
    else
      Valid(schema)
}

object ConfluentSchemaRegistryProvider extends Serializable {

  def apply(): ConfluentSchemaRegistryProvider =
    ConfluentSchemaRegistryProvider(CachedConfluentSchemaRegistryClientFactory())

  def apply(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory): ConfluentSchemaRegistryProvider =
    avroPayload(
      schemaRegistryClientFactory,
      formatKey = false
    )

  def avroPayload(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                  formatKey: Boolean): ConfluentSchemaRegistryProvider = {
    ConfluentSchemaRegistryProvider(
      schemaRegistryClientFactory,
      new ConfluentAvroSerializationSchemaFactory(schemaRegistryClientFactory),
      new ConfluentKafkaAvroDeserializationSchemaFactory(schemaRegistryClientFactory),
      formatKey)
  }

  def apply(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
            serializationSchemaFactory: KafkaAvroSerializationSchemaFactory,
            deserializationSchemaFactory: KafkaAvroDeserializationSchemaFactory,
            formatKey: Boolean): ConfluentSchemaRegistryProvider = {
    new ConfluentSchemaRegistryProvider(
      schemaRegistryClientFactory,
      serializationSchemaFactory,
      deserializationSchemaFactory,
      formatKey)
  }

  def jsonPayload(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                  formatKey: Boolean): ConfluentSchemaRegistryProvider = ConfluentSchemaRegistryProvider(schemaRegistryClientFactory,
    new ConfluentJsonPayloadSerializerFactory(schemaRegistryClientFactory),
    new ConfluentJsonPayloadDeserializerFactory(schemaRegistryClientFactory),
    formatKey)

}
