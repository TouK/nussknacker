package pl.touk.nussknacker.engine.avro.schemaregistry

import pl.touk.nussknacker.engine.kafka.RecordFormatter
import pl.touk.nussknacker.engine.kafka.serialization.{KafkaDeserializationSchemaVersionAwareFactory, KafkaSerializationSchemaVersionAwareFactory}

/**
  * @tparam T - Scheme used to deserialize
  */
trait SchemaRegistryProvider[T] extends Serializable {

  def createSchemaRegistryClient: SchemaRegistryClient

  def deserializationSchemaFactory: KafkaDeserializationSchemaVersionAwareFactory[T]

  def serializationSchemaFactory: KafkaSerializationSchemaVersionAwareFactory[Any]

  def recordFormatter(topic: String): Option[RecordFormatter]
}
