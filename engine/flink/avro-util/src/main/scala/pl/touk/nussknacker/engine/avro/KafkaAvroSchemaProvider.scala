package pl.touk.nussknacker.engine.avro

import cats.data.Validated
import org.apache.avro.Schema
import org.apache.flink.streaming.connectors.kafka.{KafkaDeserializationSchema, KafkaSerializationSchema}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryError
import pl.touk.nussknacker.engine.kafka.RecordFormatter

/**
  * @tparam T - Scheme used to deserialize
  */
trait KafkaAvroSchemaProvider[T] extends Serializable {

  def typeDefinition: Validated[SchemaRegistryError, typing.TypingResult]

  def deserializationSchema: KafkaDeserializationSchema[T]

  def serializationSchema: KafkaSerializationSchema[Any]

  def recordFormatter: Option[RecordFormatter]

  def fetchValueSchema: Validated[SchemaRegistryError, Schema]

  def returnType(f: SchemaRegistryError => typing.TypingResult): typing.TypingResult =
    typeDefinition.valueOr(f)
}
