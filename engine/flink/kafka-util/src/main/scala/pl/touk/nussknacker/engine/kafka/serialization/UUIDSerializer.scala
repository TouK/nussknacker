package pl.touk.nussknacker.engine.kafka.serialization

import java.nio.charset.StandardCharsets
import java.util
import java.util.UUID

import org.apache.kafka.common.serialization.Serializer

//This should not be used for high throughput topics - UUID.randomUUID() is not performant...
class UUIDSerializer[T] extends Serializer[T] {
  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {}

  override def serialize(topic: String, data: T): Array[Byte] = UUID.randomUUID().toString.getBytes(StandardCharsets.UTF_8)

  override def close(): Unit = {}
}
