package pl.touk.nussknacker.engine.kafka.serialization

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.kafka.KafkaConfig

/**
  * Factory class for KeyedDeserializationSchema. It is extracted for purpose when for creation
  * of KeyedDeserializationSchema are needed additional information like list of topics and configuration.
  *
  * @tparam T type of deserialized object
  */
trait KafkaDeserializationSchemaFactory[T] extends Serializable {
  def create(topics: NonEmptyList[TopicName.ForSource], kafkaConfig: KafkaConfig): KafkaDeserializationSchema[T]
}
