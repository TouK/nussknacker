package pl.touk.nussknacker.engine.kafka.serialization

import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.serialization.Deserializer

import java.util

class DelegatingKafkaDeserializer[T](delegate: Deserializer[T]) extends Deserializer[T] {

  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = delegate.configure(configs, isKey)

  override def deserialize(topic: String, data: Array[Byte]): T = {
    val deserialized = delegate.deserialize(topic, data)
    postprocess(deserialized, topic)
  }

  protected def postprocess(deserialized: T, topic: String): T = deserialized

  override def deserialize(topic: String, headers: Headers, data: Array[Byte]): T = {
    val deserialized = delegate.deserialize(topic, headers, data)
    postprocess(deserialized, topic, headers)
  }

  protected def postprocess(deserialized: T, topic: String, headers: Headers): T = deserialized

  override def close(): Unit = delegate.close()
}
