package pl.touk.nussknacker.engine.kafka.consumerrecord

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.{Deserializer, StringDeserializer}
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, serialization}

/**
  * Produces deserialization schema that describes how to turn the Kafka raw [[org.apache.kafka.clients.consumer.ConsumerRecord]]
  * (with raw key-value of type Array[Byte]) into deserialized ConsumerRecord[K, V] (with proper key-value types).
  * It allows the source to provide event value AND event metadata to the stream.
  *
  * @tparam K - type of key of deserialized ConsumerRecord
  * @tparam V - type of value of deserialized ConsumerRecord
  */
abstract class ConsumerRecordDeserializationSchemaFactory[K, V] extends KafkaDeserializationSchemaFactory[ConsumerRecord[K, V]] with Serializable {

  protected def createKeyDeserializer(kafkaConfig: KafkaConfig): Deserializer[K]

  protected def createValueDeserializer(kafkaConfig: KafkaConfig): Deserializer[V]

  override def create(topics: List[String], kafkaConfig: KafkaConfig): serialization.KafkaDeserializationSchema[ConsumerRecord[K, V]] = {

    new serialization.KafkaDeserializationSchema[ConsumerRecord[K, V]] {

      @transient
      private lazy val keyDeserializer = createKeyDeserializer(kafkaConfig)
      @transient
      private lazy val valueDeserializer = createValueDeserializer(kafkaConfig)

      override def deserialize(record: ConsumerRecord[Array[Byte], Array[Byte]]): ConsumerRecord[K, V] = {
        val key = keyDeserializer.deserialize(record.topic(), record.key())
        val value = valueDeserializer.deserialize(record.topic(), record.value())
        new ConsumerRecord[K, V](
          record.topic(),
          record.partition(),
          record.offset(),
          record.timestamp(),
          record.timestampType(),
          ConsumerRecord.NULL_CHECKSUM.toLong, // ignore deprecated checksum
          record.serializedKeySize(),
          record.serializedValueSize(),
          key,
          value,
          record.headers(),
          record.leaderEpoch()
        )
      }

      override def isEndOfStream(nextElement: ConsumerRecord[K, V]): Boolean = false
    }
  }

}

object ConsumerRecordDeserializationSchemaFactory {

  /**
    * Wrapper for function deserializing value. For kafka event key data it uses simple "Array[Byte] to String" deserialization.
    * Used with simple, value-only, sources where event key is empty or ignored.
    *
    * @param deserializeFun - function deserializing bytes to value
    * @tparam V - type of value of deserialized ConsumerRecord
    */
  def fixedValueDeserialization[V](deserializeFun: Array[Byte] => V): ConsumerRecordDeserializationSchemaFactory[String, V] =
    new ConsumerRecordDeserializationSchemaFactory[String, V] {
      override protected def createKeyDeserializer(kafkaConfig: KafkaConfig): Deserializer[String] = new StringDeserializer

      override protected def createValueDeserializer(kafkaConfig: KafkaConfig): Deserializer[V] = new Deserializer[V] {
        override def deserialize(topic: String, data: Array[Byte]): V = deserializeFun(data)
      }
    }

}
