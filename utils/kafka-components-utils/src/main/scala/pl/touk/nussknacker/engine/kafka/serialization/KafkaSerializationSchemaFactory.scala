package pl.touk.nussknacker.engine.kafka.serialization

import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.kafka.KafkaConfig

/**
  * Factory class for KeyedSerializationSchema. It is extracted for purpose when for creation
  * of KeyedSerializationSchema are needed additional information like list of topics and configuration.
  *
  * @tparam T type of serialized object
  */
trait KafkaSerializationSchemaFactory[T] extends Serializable {
  def create(topic: TopicName.ForSink, kafkaConfig: KafkaConfig): KafkaSerializationSchema[T]
}

/**
  * Base implementation of [[pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchemaFactory]]
  * Factory which always return the same schema.
  *
  * @param deserializationSchema schema which will be returned.
  * @tparam T type of serialized object
  */
case class FixedKafkaSerializationSchemaFactory[T](deserializationSchema: String => KafkaSerializationSchema[T])
    extends KafkaSerializationSchemaFactory[T] {

  override def create(topic: TopicName.ForSink, kafkaConfig: KafkaConfig): KafkaSerializationSchema[T] =
    deserializationSchema(topic.name)
}
