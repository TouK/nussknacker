package pl.touk.nussknacker.engine.avro

import org.apache.avro.Schema
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.TimestampAssigner
import pl.touk.nussknacker.engine.kafka.RecordFormatter
import pl.touk.nussknacker.engine.kafka.serialization.{DeserializationSchemaFactory, SerializationSchemaFactory}

trait SchemaRegistryClient extends Serializable {
  def schemaById(id: Int): Schema
  def schemaBySubjectAndVersion(subject: String, version: Int): String
  def latestSchema(name: String): String
}

trait SchemaRegistryProvider[T] {

  def schemaRegistryClient: SchemaRegistryClient

  def deserializationSchemaFactory: DeserializationSchemaFactory[T]

  def timestampAssigner: Option[TimestampAssigner[T]]

  def serializationSchemaFactory: SerializationSchemaFactory[Any]

  def recordFormatter(topic: String, formatKey: Boolean): RecordFormatter

}
