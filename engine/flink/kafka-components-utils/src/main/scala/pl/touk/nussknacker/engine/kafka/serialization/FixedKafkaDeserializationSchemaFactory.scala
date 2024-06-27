package pl.touk.nussknacker.engine.kafka.serialization

import cats.data.NonEmptyList
import org.apache.flink.api.common.serialization.DeserializationSchema
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.kafka.KafkaConfig

/**
  * Base implementation of [[pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchemaFactory]]
  * Factory which always return the same schema based on Flink's deserialization schema.
  *
  * @param deserializationSchema schema which will be returned.
  * @tparam T type of deserialized object
  */
case class FixedKafkaDeserializationSchemaFactory[T](deserializationSchema: KafkaDeserializationSchema[T])
    extends KafkaDeserializationSchemaFactory[T] {

  override def create(
      topics: NonEmptyList[TopicName.ForSource],
      kafkaConfig: KafkaConfig
  ): KafkaDeserializationSchema[T] =
    deserializationSchema

}

object FixedKafkaDeserializationSchemaFactory {

  def apply[T](deserializationSchema: DeserializationSchema[T]): FixedKafkaDeserializationSchemaFactory[T] = {
    new FixedKafkaDeserializationSchemaFactory(
      FlinkSerializationSchemaConversions.wrapToNuDeserializationSchema(deserializationSchema)
    )
  }

}
