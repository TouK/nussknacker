package pl.touk.nussknacker.engine.avro.schemaregistry

import pl.touk.nussknacker.engine.avro.serialization.{KafkaAvroDeserializationSchemaFactory, KafkaAvroSerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.RecordFormatter

/**
  * @tparam T - Scheme used to deserialize
  */
trait SchemaRegistryProvider[T] extends Serializable {
  def createSchemaRegistryClient: SchemaRegistryClient

  def deserializationSchemaFactory: KafkaAvroDeserializationSchemaFactory[T]

  def serializationSchemaFactory: KafkaAvroSerializationSchemaFactory[Any]

  def recordFormatter(topic: String): Option[RecordFormatter]
}
