package pl.touk.nussknacker.engine.kafka.serialization

import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.Headers
import pl.touk.nussknacker.engine.kafka.KafkaRecordUtils

import java.lang
import java.nio.charset.StandardCharsets

object schemas {

  private def safeBytes(value: String): Array[Byte] = Option(value).map(_.getBytes(StandardCharsets.UTF_8)).orNull

  // trait mostly for making java version more usable (lambda <-> function serializability problems,
  // the fact we cross compile to 2.11 only makes it worse...)
  trait ToStringSerializer[T] extends Serializable {
    def serialize(value: T): String
  }

  object ToStringSerializer {

    def apply[T](fun: T => String): ToStringSerializer[T] = new ToStringSerializer[T] {
      override def serialize(value: T): String = fun(value)
    }

  }

  trait ToHeadersSerializer[T] extends Serializable {
    def serialize(value: T): Headers
  }

  object ToHeaderMapSerializer {

    def apply[T](fun: T => Headers): ToHeadersSerializer[T] = new ToHeadersSerializer[T] {
      override def serialize(value: T): Headers = fun(value)
    }

  }

  class BaseSimpleSerializationSchema[T](
      topic: String,
      valueSerializer: ToStringSerializer[T],
      keySerializer: ToStringSerializer[T],
      headersSerializer: ToHeadersSerializer[T]
  ) extends KafkaSerializationSchema[T] {

    def this(
        topic: String,
        valueSerializer: T => String,
        keySerializer: T => String = (_: T) => null,
        headersSerializer: T => Headers = (_: T) => KafkaRecordUtils.emptyHeaders
    ) = {
      this(
        topic,
        ToStringSerializer(valueSerializer),
        ToStringSerializer(keySerializer),
        ToHeaderMapSerializer(headersSerializer)
      )
    }

    override def serialize(element: T, timestamp: lang.Long): ProducerRecord[Array[Byte], Array[Byte]] = {
      val value   = valueSerializer.serialize(element)
      val key     = Option(keySerializer).map(_.serialize(element)).orNull
      val headers = Option(headersSerializer).map(_.serialize(element)).getOrElse(KafkaRecordUtils.emptyHeaders)
      KafkaProducerHelper.createRecord(topic, safeBytes(key), safeBytes(value), timestamp, headers)
    }

  }

  class SimpleSerializationSchema[T](
      topic: String,
      valueSerializer: ToStringSerializer[T],
      keySerializer: ToStringSerializer[T]
  ) extends BaseSimpleSerializationSchema[T](topic, valueSerializer, keySerializer, null) {

    def this(topic: String, valueSerializer: T => String, keySerializer: T => String = (_: T) => null) = {
      this(topic, ToStringSerializer(valueSerializer), ToStringSerializer(keySerializer))
    }

  }

}
