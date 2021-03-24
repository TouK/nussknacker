package pl.touk.nussknacker.engine.kafka.consumerrecord

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.flink.util.source.EspDeserializationSchema

import scala.annotation.nowarn
import scala.reflect.classTag

/**
  * Produces deserialization schema that describes how to turn the Kafka raw [[org.apache.kafka.clients.consumer.ConsumerRecord]]
  * (with raw key-value of type Array[Byte]) into deserialized ConsumerRecord[K, V] (with proper key-value types).
  * It allows the source to provide event value AND event metadata to the stream.
  *
  * Deprecated annotations for checksum field mapping, to fulfill constructor requirements.
  */
@silent("deprecated")
@nowarn("deprecated")
object ConsumerRecordDeserializationSchemaFactory {

  /**
    * Creates ConsumerRecord with deserialized key and value.
    *
    * @tparam K - type of key of deserialized ConsumerRecord
    * @tparam V - type of value of deserialized ConsumerRecord
    */
  def create[K, V](keyDeserializationSchema: DeserializationSchema[K], valueDeserializationSchema: DeserializationSchema[V]): KafkaDeserializationSchema[ConsumerRecord[K, V]] = {

    val clazz = classTag[ConsumerRecord[K, V]].runtimeClass.asInstanceOf[Class[ConsumerRecord[K, V]]]

    new KafkaDeserializationSchema[ConsumerRecord[K, V]] {

      override def deserialize(record: ConsumerRecord[Array[Byte], Array[Byte]]): ConsumerRecord[K, V] = {
        val key = keyDeserializationSchema.deserialize(record.key())
        val value = valueDeserializationSchema.deserialize(record.value())
        new ConsumerRecord[K, V](
          record.topic(),
          record.partition(),
          record.offset(),
          record.timestamp(),
          record.timestampType(),
          record.checksum(),
          record.serializedKeySize(),
          record.serializedValueSize(),
          key,
          value,
          record.headers()
        )
      }

      override def isEndOfStream(nextElement: ConsumerRecord[K, V]): Boolean = false

      override def getProducedType: TypeInformation[ConsumerRecord[K, V]] = TypeInformation.of(clazz)
    }
  }

  /**
    * Creates ConsumerRecord with deserialized value only. Key is left intact.
    *
    * @tparam V - type of value of deserialized ConsumerRecord
    */
  def create[V](valueDeserializationSchema: DeserializationSchema[V]): KafkaDeserializationSchema[ConsumerRecord[Array[Byte], V]] = {
    implicit val keyTypeInformation: TypeInformation[Array[Byte]] = TypeInformation.of(classOf[Array[Byte]])
    create(new EspDeserializationSchema[Array[Byte]](identity), valueDeserializationSchema)
  }

}
