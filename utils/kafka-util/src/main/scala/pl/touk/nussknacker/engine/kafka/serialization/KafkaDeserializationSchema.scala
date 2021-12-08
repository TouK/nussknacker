package pl.touk.nussknacker.engine.kafka.serialization

import org.apache.kafka.clients.consumer.ConsumerRecord

import java.io.Serializable

trait KafkaDeserializationSchema[T] extends Serializable {
  def isEndOfStream(nextElement: T): Boolean

  @throws[Exception]
  def deserialize(record: ConsumerRecord[Array[Byte], Array[Byte]]): T
}