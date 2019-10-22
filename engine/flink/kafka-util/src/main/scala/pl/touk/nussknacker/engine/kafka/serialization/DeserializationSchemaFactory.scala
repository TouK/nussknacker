package pl.touk.nussknacker.engine.kafka.serialization

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.Deserializer
import pl.touk.nussknacker.engine.kafka.KafkaConfig

/**
  * Factory class for Flink's KeyedDeserializationSchema. It is extracted for  purpose when for creation
  * of KeyedDeserializationSchema are needed additional information like list of topics and configuration.
  *
  * @tparam T type of deserialized object
  */
trait DeserializationSchemaFactory[T] {

  def create(topics: List[String], kafkaConfig: KafkaConfig): KafkaDeserializationSchema[T]

}

/**
  * Factory which always return the same schema.
  *
  * @param deserializationSchema schema which will be returned.
  * @tparam T type of deserialized object
  */
case class FixedDeserializationSchemaFactory[T](deserializationSchema: KafkaDeserializationSchema[T])
  extends DeserializationSchemaFactory[T] {

  override def create(topics: List[String], kafkaConfig: KafkaConfig): KafkaDeserializationSchema[T] = deserializationSchema

}

/**
  * Abstract base implementation of [[pl.touk.nussknacker.engine.kafka.serialization.DeserializationSchemaFactory]]
  * which uses Kafka's Deserializer in returned Flink's KeyedDeserializationSchema. It deserializes only value.
  *
  * @tparam T type of deserialized object
  */
abstract class KafkaDeserializationSchemaFactoryBase[T: TypeInformation]
  extends DeserializationSchemaFactory[T] with Serializable {

  protected def createValueDeserializer(topics: List[String], kafkaConfig: KafkaConfig): Deserializer[T]

  override def create(topics: List[String], kafkaConfig: KafkaConfig): KafkaDeserializationSchema[T] = {
    new KafkaDeserializationSchema[T] {
      private lazy val valueDeserializer = createValueDeserializer(topics, kafkaConfig)

      override def deserialize(consumerRecord: ConsumerRecord[Array[Byte], Array[Byte]]): T = {
        val value = valueDeserializer.deserialize(consumerRecord.topic(), consumerRecord.value())
        value
      }

      override def isEndOfStream(nextElement: T): Boolean = false

      override def getProducedType: TypeInformation[T] = implicitly[TypeInformation[T]]

    }
  }

}

/**
  * Abstract base implementation of [[pl.touk.nussknacker.engine.kafka.serialization.DeserializationSchemaFactory]]
  * which uses Kafka's Deserializer in returned Flink's KeyedDeserializationSchema. It deserializes both key and value
  * and wrap it in object T
  *
  * @tparam T type of deserialized object
  */
abstract class KafkaKeyValueDeserializationSchemaFactoryBase[T: TypeInformation]
  extends DeserializationSchemaFactory[T] with Serializable {

  protected type K

  protected type V

  protected def createKeyDeserializer(topics: List[String], kafkaConfig: KafkaConfig): Deserializer[K]

  protected def createValueDeserializer(topics: List[String], kafkaConfig: KafkaConfig): Deserializer[V]

  protected def createObject(key: K, value: V, topic: String): T

  override def create(topics: List[String], kafkaConfig: KafkaConfig): KafkaDeserializationSchema[T] = {
    new KafkaDeserializationSchema[T] {
      private lazy val keyDeserializer = createKeyDeserializer(topics, kafkaConfig)
      private lazy val valueDeserializer = createValueDeserializer(topics, kafkaConfig)


      override def deserialize(consumerRecord: ConsumerRecord[Array[Byte], Array[Byte]]): T = {
        val key = keyDeserializer.deserialize(consumerRecord.topic(), consumerRecord.key())
        val value = valueDeserializer.deserialize(consumerRecord.topic(), consumerRecord.value())
        val obj = createObject(key, value, consumerRecord.topic())
        obj
      }

      override def isEndOfStream(nextElement: T): Boolean = false

      override def getProducedType: TypeInformation[T] = implicitly[TypeInformation[T]]

    }
  }

}