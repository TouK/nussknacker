package pl.touk.nussknacker.engine.avro.schemaregistry

import cats.data.ValidatedNel
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.serialization.{KafkaAvroDeserializationSchemaFactory, KafkaAvroSerializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.RecordFormatterFactory

trait SchemaRegistryProvider extends Serializable with BaseSchemaRegistryProvider {

  def deserializationSchemaFactory: KafkaAvroDeserializationSchemaFactory

  def serializationSchemaFactory: KafkaAvroSerializationSchemaFactory

  def recordFormatterFactory: RecordFormatterFactory

  def validateSchema(schema: Schema): ValidatedNel[SchemaRegistryError, Schema]
}
