package pl.touk.nussknacker.engine.kafka.consumerrecord

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchemaFactory

import scala.annotation.nowarn
import scala.reflect.{ClassTag, classTag}

/**
  * Produces deserialization schema that describes how to turn the Kafka raw [[org.apache.kafka.clients.consumer.ConsumerRecord]]
  * (with raw key-value of type Array[Byte]) into deserialized ConsumerRecord[K, V] (with proper key-value types).
  * It allows the source to provide event value AND event metadata to the stream.
  *
  * Deprecated annotations for checksum field mapping, to fulfill constructor requirements.
  *
  * @tparam K - type of key of deserialized ConsumerRecord
  * @tparam V - type of value of deserialized ConsumerRecord
  */
@silent("deprecated")
@nowarn("cat=deprecation")
class ConsumerRecordDeserializationSchemaFactory[K: ClassTag, V: ClassTag](keyDeserializationSchema: DeserializationSchema[K],
                                                                           valueDeserializationSchema: DeserializationSchema[V])
  extends KafkaDeserializationSchemaFactory[ConsumerRecord[K, V]] {

  override def create(topics: List[String], kafkaConfig: KafkaConfig): KafkaDeserializationSchema[ConsumerRecord[K, V]] = {

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

}
