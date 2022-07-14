package pl.touk.nussknacker.engine.avro.schemaregistry

import cats.data.ValidatedNel
import io.confluent.kafka.schemaregistry.ParsedSchema
import pl.touk.nussknacker.engine.avro.serialization.{KafkaAvroDeserializationSchemaFactory, KafkaAvroSerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.RecordFormatterFactory

//todo: rename and separate from BaseSchemaRegistryProvider
trait SchemaRegistryProvider[T<:ParsedSchema] extends Serializable with BaseSchemaRegistryProvider {

  def deserializationSchemaFactory: KafkaAvroDeserializationSchemaFactory[T]

  def serializationSchemaFactory: KafkaAvroSerializationSchemaFactory[T]

  def recordFormatterFactory: RecordFormatterFactory

  def validateSchema(schema: T): ValidatedNel[SchemaRegistryError, T]
}
