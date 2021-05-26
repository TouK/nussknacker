package pl.touk.nussknacker.engine.kafka.consumerrecord

import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.kafka.common.serialization.{Deserializer, StringDeserializer}
import pl.touk.nussknacker.engine.kafka.KafkaConfig

/**
  * Wrapper for value-only DeserializationSchema. For kafka event key data it uses simple "Array[Byte] to String" deserialization.
  * Used with simple, value-only, sources where event key is empty or ignored.
  *
  * @param valueSchema - value deserialization schema (e.g. EspDeserializationSchema)
  * @tparam V - type of value of deserialized ConsumerRecord
  */
class FixedValueDeserializationSchemaFactory[V](valueSchema: DeserializationSchema[V]) extends ConsumerRecordDeserializationSchemaFactory[String, V]{
  override protected def createKeyDeserializer(kafkaConfig: KafkaConfig): Deserializer[String] = new StringDeserializer
  override protected def createValueDeserializer(kafkaConfig: KafkaConfig): Deserializer[V] = new Deserializer[V] {
    override def deserialize(topic: String, data: Array[Byte]): V = valueSchema.deserialize(data)
  }
}
