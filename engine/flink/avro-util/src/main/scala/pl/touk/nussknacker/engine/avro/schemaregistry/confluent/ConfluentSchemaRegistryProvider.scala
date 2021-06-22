package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import io.circe.Json
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{CachedConfluentSchemaRegistryClientFactory, ConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter.ConfluentAvroToJsonFormatterFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.jsonpayload.{ConfluentJsonPayloadSerializerFactory, ConfluentKeyValueKafkaJsonDeserializerFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.{ConfluentAvroSerializationSchemaFactory, ConfluentKeyValueKafkaAvroDeserializationFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaRegistryError, SchemaRegistryProvider, SchemaRegistryUnsupportedTypeError}
import pl.touk.nussknacker.engine.avro.serialization.{KafkaAvroDeserializationSchemaFactory, KafkaAvroSerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.RecordFormatterFactory
import pl.touk.nussknacker.engine.kafka.consumerrecord.ConsumerRecordToJsonFormatterFactory

class ConfluentSchemaRegistryProvider(val schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                      val serializationSchemaFactory: KafkaAvroSerializationSchemaFactory,
                                      val deserializationSchemaFactory: KafkaAvroDeserializationSchemaFactory,
                                      val recordFormatterFactory: RecordFormatterFactory) extends SchemaRegistryProvider {

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
      schemaRegistryClientFactory
    )

  def avroPayload(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory): ConfluentSchemaRegistryProvider = {
    ConfluentSchemaRegistryProvider(
      schemaRegistryClientFactory,
      new ConfluentAvroSerializationSchemaFactory(schemaRegistryClientFactory),
      new ConfluentKeyValueKafkaAvroDeserializationFactory(schemaRegistryClientFactory),
      new ConfluentAvroToJsonFormatterFactory(schemaRegistryClientFactory)
    )
  }

  def apply(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
            serializationSchemaFactory: KafkaAvroSerializationSchemaFactory,
            deserializationSchemaFactory: KafkaAvroDeserializationSchemaFactory,
            recordFormatterFactory: RecordFormatterFactory): ConfluentSchemaRegistryProvider = {
    new ConfluentSchemaRegistryProvider(
      schemaRegistryClientFactory,
      serializationSchemaFactory,
      deserializationSchemaFactory,
      recordFormatterFactory
    )
  }

  def jsonPayload(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory): ConfluentSchemaRegistryProvider = {
    ConfluentSchemaRegistryProvider(
      schemaRegistryClientFactory,
      new ConfluentJsonPayloadSerializerFactory(schemaRegistryClientFactory),
      new ConfluentKeyValueKafkaJsonDeserializerFactory(schemaRegistryClientFactory),
      new ConsumerRecordToJsonFormatterFactory[Json, Json]
    )
  }

}
