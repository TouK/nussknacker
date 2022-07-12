package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import org.apache.avro.Schema
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.avro.{AvroRuntimeSchemaData, JsonRuntimeSchemaData, RuntimeSchemaData}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{CachedConfluentSchemaRegistryClientFactory, ConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter.{ConfluentAvroToJsonFormatterFactory, JsonOrAvroPayloadFormatterFactory, JsonPayloadToJsonFormatterFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.jsonpayload.{ConfluentJsonPayloadSerializerFactory, ConfluentKeyValueKafkaJsonDeserializerFactory, ConfluentKeyValueKafkaJsonOrAvroDeserializerFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.{ConfluentAvroSerializationSchemaFactory, ConfluentKeyValueKafkaAvroDeserializationFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.{AvroSchema, JsonSchema, SchemaContainer, SchemaRegistryError, SchemaRegistryProvider, SchemaRegistryUnsupportedTypeError}
import pl.touk.nussknacker.engine.avro.serialization.{KafkaAvroDeserializationSchemaFactory, KafkaAvroKeyValueDeserializationSchemaFactory, KafkaAvroSerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchema
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, RecordFormatterFactory}

import scala.reflect.ClassTag

class ConfluentSchemaRegistryProvider(val schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory,
                                      val serializationSchemaFactory: KafkaAvroSerializationSchemaFactory,
                                      val deserializationSchemaFactory: KafkaAvroDeserializationSchemaFactory,
                                      val recordFormatterFactory: RecordFormatterFactory) extends SchemaRegistryProvider {

  override def validateSchema(schema: SchemaContainer): ValidatedNel[SchemaRegistryError, SchemaContainer] = {
  /* kafka-avro-serializer does not support Array at top level
  [https://github.com/confluentinc/schema-registry/issues/1298] */
    schema match {
      case JsonSchema(s) => Valid(schema)
      case AvroSchema(s) => if (s.getType == Schema.Type.ARRAY)
        Invalid(NonEmptyList.of(
          SchemaRegistryUnsupportedTypeError("Unsupported Avro type. Top level Arrays are not supported")))
      else
        Valid(schema)
    }
  }
}

object ConfluentSchemaRegistryProvider extends Serializable {

  def apply(): ConfluentSchemaRegistryProvider =
    avroPayload(CachedConfluentSchemaRegistryClientFactory)

  def apply(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory): ConfluentSchemaRegistryProvider = {
    avroPayload(schemaRegistryClientFactory)
  }

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
      new JsonPayloadToJsonFormatterFactory
    )
  }

  // json schema -> json payload, avro schema -> avro payload
  def jsonOrAvroPayload(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory): ConfluentSchemaRegistryProvider = {
    ConfluentSchemaRegistryProvider(
      schemaRegistryClientFactory, //ok
      new ConfluentJsonPayloadSerializerFactory(schemaRegistryClientFactory), //todo
      new ConfluentKeyValueKafkaJsonOrAvroDeserializerFactory(schemaRegistryClientFactory), //done
      new JsonOrAvroPayloadFormatterFactory(schemaRegistryClientFactory) //done
    )
  }

}
