package pl.touk.nussknacker.engine.avro.schemaregistry

import pl.touk.nussknacker.engine.avro.serialization.{KafkaAvroDeserializationSchemaFactory, KafkaAvroSerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.RecordFormatter

trait SchemaRegistryProvider extends Serializable {

  def createSchemaRegistryClient: SchemaRegistryClient

  def deserializationSchemaFactory: KafkaAvroDeserializationSchemaFactory

  def serializationSchemaFactory: KafkaAvroSerializationSchemaFactory

  def recordFormatter(topic: String): Option[RecordFormatter]
}
