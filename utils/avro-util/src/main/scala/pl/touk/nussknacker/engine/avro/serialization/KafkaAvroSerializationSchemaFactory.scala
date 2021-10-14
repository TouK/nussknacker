package pl.touk.nussknacker.engine.avro.serialization

import org.apache.flink.formats.avro.typeutils.NkSerializableAvroSchema
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, serialization}

/**
  * Factory class for Flink's KeyedSerializationSchema. It is extracted for purpose when for creation
  * of KafkaSerializationSchema are needed additional avro related information. SerializationSchema will take
  * KafkaSerializationSchema with key extracted in the step before serialization
  */
trait KafkaAvroSerializationSchemaFactory[T] extends Serializable {

  def create(topic: String, version: Option[Int], schemaOpt: Option[NkSerializableAvroSchema], kafkaConfig: KafkaConfig): serialization.KafkaSerializationSchema[T]

}
