package pl.touk.nussknacker.engine.avro

import pl.touk.nussknacker.engine.kafka.RecordFormatter
import pl.touk.nussknacker.engine.kafka.serialization.{DeserializationSchemaFactory, SerializationSchemaFactory}

trait SchemaRegistryProvider[T] {

  def schemaRegistryClient: SchemaRegistryClient

  def deserializationSchemaFactory: DeserializationSchemaFactory[T]

  def serializationSchemaFactory: SerializationSchemaFactory[Any]

  def recordFormatter(topic: String): Option[RecordFormatter]

}
