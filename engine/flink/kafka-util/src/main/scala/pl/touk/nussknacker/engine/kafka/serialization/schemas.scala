package pl.touk.nussknacker.engine.kafka.serialization

import java.lang
import java.nio.charset.StandardCharsets

import io.circe.Encoder
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema
import org.apache.kafka.clients.producer.ProducerRecord

import scala.language.implicitConversions

object schemas {

  private def safeBytes(value: String): Array[Byte] = Option(value).map(_.getBytes(StandardCharsets.UTF_8)).orNull

  //trait mostly for making java version more usable (lambda <-> function serializability problems,
  // the fact we cross compile to 2.11 only makes it worse...)
  trait ToStringSerializer[T] extends Serializable {
    def serialize(value: T): String
  }

  object ToStringSerializer {
    def apply[T](fun: T => String): ToStringSerializer[T] = new ToStringSerializer[T] {
      override def serialize(value: T): String = fun(value)
    }
  }

  class SimpleSerializationSchema[T](topic: String,
                                     valueSerializer: ToStringSerializer[T],
                                     keySerializer: ToStringSerializer[T])
    extends KafkaSerializationSchema[T] {

    def this(topic: String, valueSerializer: T => String, keySerializer: T => String = (_: T) => null) = {
      this(topic, ToStringSerializer(valueSerializer), ToStringSerializer(keySerializer))
    }

    override def serialize(element: T, timestamp: lang.Long): ProducerRecord[Array[Byte], Array[Byte]] = {
      val value = valueSerializer.serialize(element)
      val key = Option(keySerializer).map(_.serialize(element)).orNull
      //Kafka timestamp has to be >= 0, while Flink can use Long.MinValue
      val timestampForKafka = Math.max(0, timestamp)
      new ProducerRecord[Array[Byte], Array[Byte]](topic, null, timestampForKafka, safeBytes(key), safeBytes(value))
    }
  }

  class JsonSerializationSchema[T: Encoder](topic: String, keySerializer: T => String = (_: T) => null)
    extends SimpleSerializationSchema[T](topic, ToStringSerializer[T](v => implicitly[Encoder[T]].apply(v).noSpaces), ToStringSerializer[T](keySerializer))


}
