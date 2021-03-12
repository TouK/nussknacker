package pl.touk.nussknacker.engine.kafka.consumerrecord

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.kafka.KafkaRecordHelper

import scala.reflect.classTag

object ConsumerRecordDeserializationSchemaFactory {

  def create[K,V](keyConverter: Array[Byte] => K, valueConverter: Array[Byte] => V): KafkaDeserializationSchema[DeserializedConsumerRecord[K,V]] = {

    type R = DeserializedConsumerRecord[K,V]

    val clazz = classTag[R].runtimeClass.asInstanceOf[Class[R]]

    new KafkaDeserializationSchema[R] {

      override def deserialize(consumerRecord: ConsumerRecord[Array[Byte], Array[Byte]]): R = {
        DeserializedConsumerRecord[K,V](
          value = valueConverter(consumerRecord.value()),
          key = Option(keyConverter(consumerRecord.key())),
          topic = consumerRecord.topic,
          partition = consumerRecord.partition(),
          offset = consumerRecord.offset(),
          timestamp = consumerRecord.timestamp(),
          headers = KafkaRecordHelper.toMap(consumerRecord.headers())
        )
      }

      override def isEndOfStream(nextElement: R): Boolean = false

      override def getProducedType: TypeInformation[R] = TypeInformation.of(clazz)
    }
  }

  def create[V](valueConverter: Array[Byte] => V): KafkaDeserializationSchema[DeserializedConsumerRecord[String,V]] = {
    type R = DeserializedConsumerRecord[String,V]

    val clazz = classTag[R].runtimeClass.asInstanceOf[Class[R]]

    new KafkaDeserializationSchema[R] {

      override def deserialize(consumerRecord: ConsumerRecord[Array[Byte], Array[Byte]]): R = {
        DeserializedConsumerRecord[String,V](
          value = valueConverter(consumerRecord.value()),
          key = None,
          topic = consumerRecord.topic,
          partition = consumerRecord.partition(),
          offset = consumerRecord.offset(),
          timestamp = consumerRecord.timestamp(),
          headers = KafkaRecordHelper.toMap(consumerRecord.headers())
        )
      }

      override def isEndOfStream(nextElement: R): Boolean = false

      override def getProducedType: TypeInformation[R] = TypeInformation.of(clazz)
    }
  }
}
