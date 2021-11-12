package pl.touk.nussknacker.engine.kafka.serialization

import pl.touk.nussknacker.engine.kafka.KafkaConfig

/**
  * Factory class for KeyedDeserializationSchema. It is extracted for purpose when for creation
  * of KeyedDeserializationSchema are needed additional information like list of topics and configuration.
  *
  * @tparam T type of deserialized object
  */
trait KafkaDeserializationSchemaFactory[T] extends Serializable {
  def create(topics: List[String], kafkaConfig: KafkaConfig): KafkaDeserializationSchema[T]
}

