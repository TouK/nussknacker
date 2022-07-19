package pl.touk.nussknacker.engine.avro.schemaregistry

import cats.data.ValidatedNel
import io.confluent.kafka.schemaregistry.ParsedSchema
import pl.touk.nussknacker.engine.avro.serialization.{KafkaSchemaBasedDeserializationSchemaFactory, KafkaSchemaBasedSerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.RecordFormatterFactory

trait SchemaBasedMessagesSerdeProvider extends Serializable {

  def deserializationSchemaFactory: KafkaSchemaBasedDeserializationSchemaFactory

  def serializationSchemaFactory: KafkaSchemaBasedSerializationSchemaFactory

  def recordFormatterFactory: RecordFormatterFactory

  def validateSchema[T <: ParsedSchema](schema: T): ValidatedNel[SchemaRegistryError, T]
}
