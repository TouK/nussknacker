package pl.touk.nussknacker.engine.kafka.serialization

import java.lang
import java.nio.charset.StandardCharsets

import io.circe.Encoder
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema
import org.apache.kafka.clients.producer.ProducerRecord
import pl.touk.nussknacker.engine.kafka.KafkaRecordHelper

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

  trait ToHeaderMapSerializer[T] extends Serializable {
    def serialize(value: T): Map[String, Option[String]]
  }
  object ToHeaderMapSerializer {
    def apply[T](fun: T => Map[String, Option[String]]): ToHeaderMapSerializer[T] = new ToHeaderMapSerializer[T] {
      override def serialize(value: T): Map[String, Option[String]] = fun(value)
    }
  }

  class BaseSimpleSerializationSchema[T](topic: String,
                                         valueSerializer: ToStringSerializer[T],
                                         keySerializer: ToStringSerializer[T],
                                         headerSerializer: ToHeaderMapSerializer[T])
    extends KafkaSerializationSchema[T] {

    def this(topic: String, valueSerializer: T => String, keySerializer: T => String = (_: T) => null, headerMapSerializer: T => Map[String, Option[String]] = (_: T) => Map.empty) = {
      this(topic, ToStringSerializer(valueSerializer), ToStringSerializer(keySerializer), ToHeaderMapSerializer(headerMapSerializer))
    }

    override def serialize(element: T, timestamp: lang.Long): ProducerRecord[Array[Byte], Array[Byte]] = {
      val value = valueSerializer.serialize(element)
      val key = Option(keySerializer).map(_.serialize(element)).orNull
      val headers = Option(headerSerializer).map(_.serialize(element)).map(KafkaRecordHelper.toHeaders).getOrElse(KafkaRecordHelper.emptyHeaders)
      KafkaProducerHelper.createRecord(topic, safeBytes(key), safeBytes(value), timestamp, headers)
    }
  }

  class SimpleSerializationSchema[T](topic: String,
                                     valueSerializer: ToStringSerializer[T],
                                     keySerializer: ToStringSerializer[T])
    extends BaseSimpleSerializationSchema[T](topic, valueSerializer, keySerializer, null) {

    def this(topic: String, valueSerializer: T => String, keySerializer: T => String = (_: T) => null) = {
      this(topic, ToStringSerializer(valueSerializer), ToStringSerializer(keySerializer))
    }

  }

  class JsonSerializationSchema[T: Encoder](topic: String, keySerializer: T => String = (_: T) => null)
    extends SimpleSerializationSchema[T](topic, ToStringSerializer[T](v => implicitly[Encoder[T]].apply(v).noSpaces), ToStringSerializer[T](keySerializer))


}
