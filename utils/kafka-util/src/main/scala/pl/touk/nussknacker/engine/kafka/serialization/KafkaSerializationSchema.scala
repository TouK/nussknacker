package pl.touk.nussknacker.engine.kafka.serialization

import org.apache.kafka.clients.producer.ProducerRecord

import java.io.Serializable

trait KafkaSerializationSchema[T] extends Serializable {

  def serialize(element: T, timestamp: Long): ProducerRecord[Array[Byte], Array[Byte]]
}
