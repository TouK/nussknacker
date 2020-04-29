package pl.touk.nussknacker.engine.avro

import cats.data.Validated
import org.apache.flink.streaming.connectors.kafka.{KafkaDeserializationSchema, KafkaSerializationSchema}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.kafka.RecordFormatter

/**
  * @tparam T - Scheme used to deserialize
  */
trait KafkaAvroSchemaProvider[T] extends Serializable {

  /**
    * TODO: Create mechanism which one allows to throw exception per Node and Param
    */
  def typeDefinition: Validated[KafkaAvroException, typing.TypingResult]

  def deserializationSchema: KafkaDeserializationSchema[T]

  def serializationSchema: KafkaSerializationSchema[Any]

  def recordFormatter: Option[RecordFormatter]
}

case class KafkaAvroException(message: String) extends RuntimeException(message)
