package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.formatter.{ConfluentAvroToJsonFormatterFactory, JsonPayloadToJsonFormatterFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.jsonpayload.{ConfluentJsonPayloadSerializerFactory, ConfluentKeyValueKafkaJsonDeserializerFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.{ConfluentAvroSerializationSchemaFactory, ConfluentKeyValueKafkaAvroDeserializationFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaBasedMessagesSerdeProvider, SchemaRegistryError, SchemaRegistryUnsupportedTypeError}
import pl.touk.nussknacker.engine.avro.serialization.{KafkaAvroDeserializationSchemaFactory, KafkaAvroSerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.RecordFormatterFactory

class ConfluentSchemaBasedMessagesSerdeProvider(val serializationSchemaFactory: KafkaAvroSerializationSchemaFactory,
                                                val deserializationSchemaFactory: KafkaAvroDeserializationSchemaFactory,
                                                val recordFormatterFactory: RecordFormatterFactory) extends SchemaBasedMessagesSerdeProvider {
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

object ConfluentSchemaBasedMessagesSerdeProvider extends Serializable {

  def avroPayload(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory): ConfluentSchemaBasedMessagesSerdeProvider = {
    ConfluentSchemaBasedMessagesSerdeProvider(
      new ConfluentAvroSerializationSchemaFactory(schemaRegistryClientFactory),
      new ConfluentKeyValueKafkaAvroDeserializationFactory(schemaRegistryClientFactory),
      new ConfluentAvroToJsonFormatterFactory(schemaRegistryClientFactory)
    )
  }

  def jsonPayload(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory): ConfluentSchemaBasedMessagesSerdeProvider = {
    ConfluentSchemaBasedMessagesSerdeProvider(
      new ConfluentJsonPayloadSerializerFactory(schemaRegistryClientFactory),
      new ConfluentKeyValueKafkaJsonDeserializerFactory(schemaRegistryClientFactory),
      new JsonPayloadToJsonFormatterFactory
    )
  }

  private def apply(serializationSchemaFactory: KafkaAvroSerializationSchemaFactory,
                    deserializationSchemaFactory: KafkaAvroDeserializationSchemaFactory,
                    recordFormatterFactory: RecordFormatterFactory): ConfluentSchemaBasedMessagesSerdeProvider = {
    new ConfluentSchemaBasedMessagesSerdeProvider(
      serializationSchemaFactory,
      deserializationSchemaFactory,
      recordFormatterFactory
    )
  }
}
