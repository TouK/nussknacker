package pl.touk.nussknacker.engine.avro.schemaregistry

import pl.touk.nussknacker.engine.kafka.RecordFormatter
import pl.touk.nussknacker.engine.kafka.serialization.{KafkaVersionAwareDeserializationSchemaFactory, KafkaVersionAwareSerializationSchemaFactory}

/**
  * @tparam T - Scheme used to deserialize
  */
trait SchemaRegistryProvider[T] extends Serializable {

  def createSchemaRegistryClient: SchemaRegistryClient

  def deserializationSchemaFactory: KafkaVersionAwareDeserializationSchemaFactory[T]

  def serializationSchemaFactory: KafkaVersionAwareSerializationSchemaFactory[AnyRef]

  def recordFormatter(topic: String): Option[RecordFormatter]
}
