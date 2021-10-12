package pl.touk.nussknacker.engine.kafka.serialization

import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.kafka.KafkaConfig

/**
  * Factory class for Flink's KeyedDeserializationSchema. It is extracted for purpose when for creation
  * of KeyedDeserializationSchema are needed additional information like list of topics and configuration.
  *
  * @tparam T type of deserialized object
  */
trait KafkaDeserializationSchemaFactory[T] extends Serializable {
  def create(topics: List[String], kafkaConfig: KafkaConfig): KafkaDeserializationSchema[T]
}

/**
  * Base implementation of [[pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchemaFactory]]
  * Factory which always return the same schema.
  *
  * @param deserializationSchema schema which will be returned.
  * @tparam T type of deserialized object
  */
case class FixedKafkaDeserializationSchemaFactory[T](deserializationSchema: KafkaDeserializationSchema[T])
  extends KafkaDeserializationSchemaFactory[T] {

  override def create(topics: List[String], kafkaConfig: KafkaConfig): KafkaDeserializationSchema[T] =
    deserializationSchema
}

object FixedKafkaDeserializationSchemaFactory {


  def apply[T](deserializationSchema: DeserializationSchema[T]): FixedKafkaDeserializationSchemaFactory[T] = {
    def wrap(deserializationSchema: DeserializationSchema[T]): KafkaDeserializationSchema[T] = new KafkaDeserializationSchema[T] {
      override def isEndOfStream(nextElement: T): Boolean = deserializationSchema.isEndOfStream(nextElement)

      override def deserialize(record: ConsumerRecord[Array[Byte], Array[Byte]]): T = deserializationSchema.deserialize(record.value())
    }

    new FixedKafkaDeserializationSchemaFactory(wrap(deserializationSchema))
  }
}
