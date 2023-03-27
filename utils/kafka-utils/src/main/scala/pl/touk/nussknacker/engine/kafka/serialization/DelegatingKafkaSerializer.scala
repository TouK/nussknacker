package pl.touk.nussknacker.engine.kafka.serialization

import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.serialization.Serializer

import java.util

class DelegatingKafkaSerializer[T](delegate: Serializer[T]) extends Serializer[T] {
  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = delegate.configure(configs, isKey)

  override def serialize(topic: String, data: T): Array[Byte] = {
    val preprocessedData = preprocessData(data, topic)
    delegate.serialize(topic, preprocessedData)
  }

  protected def preprocessData(data: T, topic: String): T = data

  override def serialize(topic: String, headers: Headers, data: T): Array[Byte] = {
    val preprocessedData = preprocessData(data, topic, headers)
    delegate.serialize(topic, headers, preprocessedData)
  }
  protected def preprocessData(data: T, topic: String, headers: Headers): T = data

  override def close(): Unit = delegate.close()

}
