package pl.touk.nussknacker.engine.avro.schemaregistry

import cats.data.ValidatedNel
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.serialization.{KafkaAvroDeserializationSchemaFactory, KafkaAvroSerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.RecordFormatter

trait SchemaRegistryProvider extends Serializable {

  def createSchemaRegistryClient: SchemaRegistryClient

  def deserializationSchemaFactory(useStringAsKey: Boolean): KafkaAvroDeserializationSchemaFactory

  def serializationSchemaFactory: KafkaAvroSerializationSchemaFactory

  def recordFormatter: RecordFormatter

  def validateSchema(schema: Schema): ValidatedNel[SchemaRegistryError, Schema]
}
