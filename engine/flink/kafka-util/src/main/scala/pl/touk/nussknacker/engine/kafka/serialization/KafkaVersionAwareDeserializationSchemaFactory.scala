package pl.touk.nussknacker.engine.kafka.serialization

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.kafka.KafkaConfig

import scala.reflect.ClassTag

/**
  * Factory class for Flink's KeyedDeserializationSchema. It is extracted for purpose when for creation
  * of KeyedDeserializationSchema are needed additional information like list of topics, schema version and configuration.
  *
  * @tparam T type of deserialized object
  */
trait KafkaVersionAwareDeserializationSchemaFactory[T] extends KafkaDeserializationSchemaFactory[T] {
  def create(topics: List[String], version: Option[Int], kafkaConfig: KafkaConfig): KafkaDeserializationSchema[T]

  override def create(topics: List[String], kafkaConfig: KafkaConfig): KafkaDeserializationSchema[T] =
    create(topics, None, kafkaConfig)
}

/**
  * Abstract base implementation of [[pl.touk.nussknacker.engine.kafka.serialization.KafkaVersionAwareDeserializationSchemaFactory]]
  * which uses Kafka's Deserializer in returned Flink's KeyedDeserializationSchema. It deserializes only value.
  *
  * @tparam T type of deserialized object
  */
abstract class KafkaVersionAwareValueDeserializationSchemaFactory[T]
  extends KafkaVersionAwareDeserializationSchemaFactory[T] {

  protected def createValueDeserializer(topics: List[String], version: Option[Int], kafkaConfig: KafkaConfig): (Deserializer[T], TypeInformation[T])

  override def create(topics: List[String], version: Option[Int], kafkaConfig: KafkaConfig): KafkaDeserializationSchema[T] = {
    new KafkaDeserializationSchema[T] {
      @transient
      private lazy val deserializerWithTypeInfo = createValueDeserializer(topics, version, kafkaConfig)

      override def deserialize(consumerRecord: ConsumerRecord[Array[Byte], Array[Byte]]): T = {
        val value = deserializerWithTypeInfo._1.deserialize(consumerRecord.topic(), consumerRecord.value())
        value
      }

      override def isEndOfStream(nextElement: T): Boolean = false

      override def getProducedType: TypeInformation[T] = deserializerWithTypeInfo._2
    }
  }

}

/**
  * Abstract base implementation of [[pl.touk.nussknacker.engine.kafka.serialization.KafkaVersionAwareDeserializationSchemaFactory]]
  * which uses Kafka's Deserializer in returned Flink's KeyedDeserializationSchema. It deserializes both key and value
  * and wrap it in object T
  *
  * @tparam T type of deserialized object
  */
abstract class KafkaVersionAwareKeyValueDeserializationSchemaFactory[T]
  extends KafkaVersionAwareDeserializationSchemaFactory[T] {

  protected type K

  protected type V

  protected def keyClassTag: ClassTag[K]

  protected def valueClassTag: ClassTag[V]

  protected def createKeyDeserializer(topics: List[String], version: Option[Int], kafkaConfig: KafkaConfig): (Deserializer[K], TypeInformation[K])

  protected def createValueDeserializer(topics: List[String], version: Option[Int], kafkaConfig: KafkaConfig): (Deserializer[V], TypeInformation[V])

  protected def createObject(key: K, value: V, topic: String): T

  protected def createObjectTypeInformation(keyTypeInformation: TypeInformation[K], valueTypeInformation: TypeInformation[V]): TypeInformation[T]

  override def create(topics: List[String], version: Option[Int], kafkaConfig: KafkaConfig): KafkaDeserializationSchema[T] = {
    new KafkaDeserializationSchema[T] {
      @transient
      private lazy val keyDeserializerWithTypeInfo = createKeyDeserializer(topics, None, kafkaConfig) // We currently support only last version for keys
      @transient
      private lazy val valueDeserializerWithTypeInfo = createValueDeserializer(topics, version, kafkaConfig)

      override def deserialize(consumerRecord: ConsumerRecord[Array[Byte], Array[Byte]]): T = {
        val key = keyDeserializerWithTypeInfo._1.deserialize(consumerRecord.topic(), consumerRecord.key())
        val value = valueDeserializerWithTypeInfo._1.deserialize(consumerRecord.topic(), consumerRecord.value())
        val obj = createObject(key, value, consumerRecord.topic())
        obj
      }

      override def isEndOfStream(nextElement: T): Boolean = false

      override def getProducedType: TypeInformation[T] = createObjectTypeInformation(keyDeserializerWithTypeInfo._2, valueDeserializerWithTypeInfo._2)
    }
  }
}
