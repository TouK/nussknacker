package pl.touk.nussknacker.engine.avro

import org.apache.flink.streaming.connectors.kafka.{KafkaDeserializationSchema, KafkaSerializationSchema}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.kafka.RecordFormatter

/**
  * @tparam T - Scheme used to deserialize
  */
trait KafkaAvroSchemaProvider[T] extends Serializable {

  def typeDefinition: typing.TypingResult

  def deserializationSchema: KafkaDeserializationSchema[T]

  def serializationSchema: KafkaSerializationSchema[Any]

  def recordFormatter: Option[RecordFormatter]

}
