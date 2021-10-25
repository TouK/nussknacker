package pl.touk.nussknacker.engine.kafka.serialization

import java.lang
import java.nio.charset.StandardCharsets
import io.circe.Encoder
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.connectors.kafka
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.Headers
import pl.touk.nussknacker.engine.kafka.{ConsumerRecordUtils, serialization}

import scala.language.implicitConversions
import scala.reflect.classTag

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

  trait ToHeadersSerializer[T] extends Serializable {
    def serialize(value: T): Headers
  }
  object ToHeaderMapSerializer {
    def apply[T](fun: T => Headers): ToHeadersSerializer[T] = new ToHeadersSerializer[T] {
      override def serialize(value: T): Headers = fun(value)
    }
  }

  class BaseSimpleSerializationSchema[T](topic: String,
                                         valueSerializer: ToStringSerializer[T],
                                         keySerializer: ToStringSerializer[T],
                                         headersSerializer: ToHeadersSerializer[T])
    extends KafkaSerializationSchema[T] {

    def this(topic: String, valueSerializer: T => String, keySerializer: T => String = (_: T) => null, headersSerializer: T => Headers = (_: T) => ConsumerRecordUtils.emptyHeaders) = {
      this(topic, ToStringSerializer(valueSerializer), ToStringSerializer(keySerializer), ToHeaderMapSerializer(headersSerializer))
    }

    override def serialize(element: T, timestamp: lang.Long): ProducerRecord[Array[Byte], Array[Byte]] = {
      val value = valueSerializer.serialize(element)
      val key = Option(keySerializer).map(_.serialize(element)).orNull
      val headers = Option(headersSerializer).map(_.serialize(element)).getOrElse(ConsumerRecordUtils.emptyHeaders)
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

  def wrapToFlinkDeserializationSchema[T](deserializationSchema: serialization.KafkaDeserializationSchema[T]): kafka.KafkaDeserializationSchema[T] = {
    new kafka.KafkaDeserializationSchema[T] {

      override def getProducedType: TypeInformation[T] = {
        val clazz = classTag.runtimeClass.asInstanceOf[Class[T]]
        TypeInformation.of(clazz)
      }

      override def isEndOfStream(nextElement: T): Boolean = deserializationSchema.isEndOfStream(nextElement)

      override def deserialize(record: ConsumerRecord[Array[Byte], Array[Byte]]): T = deserializationSchema.deserialize(record)
    }
  }

  def wrapToFlinkSerializationSchema[T](serializationSchema: serialization.KafkaSerializationSchema[T]) = new kafka.KafkaSerializationSchema[T] {
    override def serialize(element: T, timestamp: lang.Long): ProducerRecord[Array[Byte], Array[Byte]] = serializationSchema.serialize(element, timestamp)
  }

}
