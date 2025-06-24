package pl.touk.nussknacker.engine.kafka.consumerrecord

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchema

trait ConsumerRecordKafkaDeserializationSchema[K, V] extends KafkaDeserializationSchema[ConsumerRecord[K, V]] {

  @transient
  protected val keyDeserializer: Deserializer[K]

  @transient
  protected val valueDeserializer: Deserializer[V]

  override def deserialize(record: ConsumerRecord[Array[Byte], Array[Byte]]): ConsumerRecord[K, V] = {
    val key   = keyDeserializer.deserialize(record.topic(), record.headers(), record.key())
    val value = valueDeserializer.deserialize(record.topic(), record.headers(), record.value())

    new ConsumerRecord[K, V](
      record.topic(),
      record.partition(),
      record.offset(),
      record.timestamp(),
      record.timestampType(),
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
