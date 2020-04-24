package pl.touk.nussknacker.engine.avro

import pl.touk.nussknacker.engine.kafka.RecordFormatter
import pl.touk.nussknacker.engine.kafka.serialization.{DeserializationSchemaFactory, SerializationSchemaFactory}

/**
  * @tparam T - Scheme used to deserialize
  */
trait SchemaRegistryProvider[T] extends Serializable {

  def createSchemaRegistryClient: SchemaRegistryClient

  def deserializationSchemaFactory: DeserializationSchemaFactory[T]

  def serializationSchemaFactory: SerializationSchemaFactory[Any]

  def recordFormatter(topic: String): Option[RecordFormatter]

}
