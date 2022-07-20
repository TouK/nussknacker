package pl.touk.nussknacker.engine.avro.serialization

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.flink.formats.avro.typeutils.NkSerializableParsedSchema
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, serialization}
import pl.touk.nussknacker.engine.util.KeyedValue

/**
  * Factory class for Flink's KeyedSerializationSchema. It is extracted for purpose when for creation
  * of KafkaSerializationSchema are needed additional avro related information. SerializationSchema will take
  * KafkaSerializationSchema with key extracted in the step before serialization
  */
trait KafkaSchemaBasedSerializationSchemaFactory extends Serializable {

  def create(topic: String, version: Option[Int], schemaOpt: Option[NkSerializableParsedSchema[AvroSchema]], kafkaConfig: KafkaConfig): serialization.KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]]

}
