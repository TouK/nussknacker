package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter.{ConfluentAvroToJsonFormatterFactory, JsonPayloadToJsonFormatterFactory, UniversalToJsonFormatterFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization._
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.jsonpayload.{ConfluentJsonPayloadSerializerFactory, ConfluentKeyValueKafkaJsonDeserializerFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.universal.{ConfluentKeyValueUniversalKafkaDeserializationFactory, ConfluentUniversalKafkaSerializationSchemaFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaBasedSerdeProvider, SchemaRegistryError, SchemaRegistryUnsupportedTypeError}
import pl.touk.nussknacker.engine.avro.serialization.{KafkaSchemaBasedDeserializationSchemaFactory, KafkaSchemaBasedSerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.RecordFormatterFactory

class ConfluentSchemaBasedSerdeProvider(val serializationSchemaFactory: KafkaSchemaBasedSerializationSchemaFactory,
                                        val deserializationSchemaFactory: KafkaSchemaBasedDeserializationSchemaFactory,
                                        val recordFormatterFactory: RecordFormatterFactory) extends SchemaBasedSerdeProvider {
  override def validateSchema[T <: ParsedSchema](schema: T): ValidatedNel[SchemaRegistryError, T] = {
    schema match {
      case s: AvroSchema => {
        /* kafka-avro-serializer does not support Array at top level
        [https://github.com/confluentinc/schema-registry/issues/1298] */
        if (s.rawSchema().getType == Schema.Type.ARRAY)
          Invalid(NonEmptyList.of(
            SchemaRegistryUnsupportedTypeError("Unsupported Avro type. Top level Arrays are not supported")))
        else
          Valid(schema)
      }
      case _ => throw new IllegalArgumentException("Unsupported schema type")
    }
  }
}

object ConfluentSchemaBasedSerdeProvider extends Serializable {

  def universal(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory): ConfluentSchemaBasedSerdeProvider = {
    ConfluentSchemaBasedSerdeProvider(
      new ConfluentUniversalKafkaSerializationSchemaFactory(schemaRegistryClientFactory),
      new ConfluentKeyValueUniversalKafkaDeserializationFactory(schemaRegistryClientFactory),
      new UniversalToJsonFormatterFactory(schemaRegistryClientFactory)
    )
  }

  def avroPayload(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory): ConfluentSchemaBasedSerdeProvider = {
    ConfluentSchemaBasedSerdeProvider(
      new ConfluentAvroSerializationSchemaFactory(schemaRegistryClientFactory),
      new ConfluentKeyValueKafkaAvroDeserializationFactory(schemaRegistryClientFactory),
      new ConfluentAvroToJsonFormatterFactory(schemaRegistryClientFactory)
    )
  }

  def jsonPayload(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory): ConfluentSchemaBasedSerdeProvider = {
    ConfluentSchemaBasedSerdeProvider(
      new ConfluentJsonPayloadSerializerFactory(schemaRegistryClientFactory),
      new ConfluentKeyValueKafkaJsonDeserializerFactory(schemaRegistryClientFactory),
      new JsonPayloadToJsonFormatterFactory
    )
  }

  private def apply(serializationSchemaFactory: KafkaSchemaBasedSerializationSchemaFactory,
                    deserializationSchemaFactory: KafkaSchemaBasedDeserializationSchemaFactory,
                    recordFormatterFactory: RecordFormatterFactory): ConfluentSchemaBasedSerdeProvider = {
    new ConfluentSchemaBasedSerdeProvider(
      serializationSchemaFactory,
      deserializationSchemaFactory,
      recordFormatterFactory
    )
  }
}
