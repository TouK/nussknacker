package pl.touk.nussknacker.engine.kafka.serialization

import java.lang
import java.nio.charset.StandardCharsets

import io.circe.Encoder
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema
import org.apache.kafka.clients.producer.ProducerRecord

object schemas {

  private def safeBytes(value: String): Array[Byte] = Option(value).map(_.getBytes(StandardCharsets.UTF_8)).orNull

  //trait mostly for making java version more usable (lambda <-> function serializability problems)
  trait ToStringSerializer[T] extends Serializable with (T => String)

  object SimpleSerializationSchema {
    def apply[T](topic: String, valueSerializer: T => String, keySerializer: T => String = (_:T) => null): SimpleSerializationSchema[T] = {
      new SimpleSerializationSchema[T](topic, valueSerializer(_), keySerializer(_))
    }
  }

  class SimpleSerializationSchema[T](topic: String,
                                     valueSerializer: ToStringSerializer[T],
                                     keySerializer: ToStringSerializer[T] = (_:T) => null)
    extends KafkaSerializationSchema[T] {

    override def serialize(element: T, timestamp: lang.Long): ProducerRecord[Array[Byte], Array[Byte]] = {
      val value = valueSerializer(element)
      val key = Option(keySerializer).map(_.apply(element)).orNull
      new ProducerRecord[Array[Byte], Array[Byte]](topic, null, timestamp, safeBytes(key), safeBytes(value))
    }
  }

  class JsonSerializationSchema[T:Encoder](topic: String, keySerializer: T => String = (_:T)  => null)
    extends SimpleSerializationSchema[T](topic, v => implicitly[Encoder[T]].apply(v).noSpaces, keySerializer(_))

}
