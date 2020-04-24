package pl.touk.nussknacker.engine.avro

import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.kafka.RecordFormatter
import pl.touk.nussknacker.engine.kafka.serialization.{DeserializationSchemaFactory, SerializationSchemaFactory}

/**
  * @tparam T - Scheme used to deserialize
  */
trait SchemaAvroProvider[T] {

  def typeDefinition(topic: String, version: Option[Int], avro: Option[String]): typing.TypingResult

  def deserializationSchemaFactory(avro: Option[String]): DeserializationSchemaFactory[T]

  def serializationSchemaFactory(avro: Option[String]): SerializationSchemaFactory[Any]

  def recordFormatter(topic: String, avro: Option[String]): Option[RecordFormatter]

}
