package pl.touk.nussknacker.engine.avro.schemaregistry

import pl.touk.nussknacker.engine.avro.serialization.{KafkaAvroDeserializationSchemaFactory, KafkaAvroSerializationSchemaFactory}
import pl.touk.nussknacker.engine.avro.typed.AvroSettings
import pl.touk.nussknacker.engine.kafka.RecordFormatter

trait SchemaRegistryProvider extends Serializable {

  def createSchemaRegistryClient: SchemaRegistryClient

  def deserializationSchemaFactory(avroSettings: AvroSettings): KafkaAvroDeserializationSchemaFactory

  def serializationSchemaFactory(avroSettings: AvroSettings): KafkaAvroSerializationSchemaFactory

  def recordFormatter(topic: String): Option[RecordFormatter]
}
