package pl.touk.nussknacker.engine.kafka.serialization

import java.nio.charset.StandardCharsets
import java.util
import java.util.UUID

import org.apache.flink.streaming.util.serialization.KeyedSerializationSchema
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.kafka.KafkaConfig

/**
  * Factory class for Flink's KeyedSerializationSchema. It is extracted for  purpose when for creation
  * of KeyedSerializationSchema are needed additional information like list of topics and configuration.
  *
  * @tparam T type of serialized object
  */
trait SerializationSchemaFactory[T] {

  def create(topic: String, kafkaConfig: KafkaConfig): KeyedSerializationSchema[T]

}

/**
  * Factory which always return the same schema.
  *
  * @param deserializationSchema schema which will be returned.
  * @tparam T type of serialized object
  */
case class FixedSerializationSchemaFactory[T](deserializationSchema: KeyedSerializationSchema[T])
  extends SerializationSchemaFactory[T] {

  override def create(topic: String, kafkaConfig: KafkaConfig): KeyedSerializationSchema[T] = deserializationSchema

}

/**
  * Abstract base implementation of [[pl.touk.nussknacker.engine.kafka.serialization.SerializationSchemaFactory]]
  * which uses Kafka's Serializer in returned Flink's KeyedSerializationSchema. It serializes only value - key will be
  * randomly generated as a UUID.
  *
  * @tparam T type of serialized object
  */
abstract class KafkaSerializationSchemaFactoryBase[T] extends SerializationSchemaFactory[T] with Serializable {

  protected def createValueSerializer(topic: String, kafkaConfig: KafkaConfig): Serializer[T]

  protected def createKeySerializer(topic: String, kafkaConfig: KafkaConfig): Serializer[T] = new UUIDSerializer[T]

  override def create(topic: String, kafkaConfig: KafkaConfig): KeyedSerializationSchema[T] = {
    new KeyedSerializationSchema[T] {

      private lazy val valueSerializer = createValueSerializer(topic, kafkaConfig)

      private lazy val keySerializer = createKeySerializer(topic, kafkaConfig)

      override def serializeKey(element: T): Array[Byte] = {
        keySerializer.serialize(topic, element)
      }

      override def serializeValue(element: T): Array[Byte] = {
        valueSerializer.serialize(topic, element)
      }

      override def getTargetTopic(element: T): String = null
    }
  }

}

//This should not be used for high throughput topics - UUID.randomUUID() is not performant...
private class UUIDSerializer[T] extends Serializer[T] {
  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {}

  override def serialize(topic: String, data: T): Array[Byte] = UUID.randomUUID().toString.getBytes(StandardCharsets.UTF_8)

  override def close(): Unit = {}
}

/**
  * Abstract base implementation of [[pl.touk.nussknacker.engine.kafka.serialization.SerializationSchemaFactory]]
  * which uses Kafka's Serializer in returned Flink's KeyedSerializationSchema. It serializes both key and value.
  *
  * @tparam T type of serialized object
  */
abstract class KafkaKeyValueSerializationSchemaFactoryBase[T] extends SerializationSchemaFactory[T] with Serializable {

  protected type K

  protected type V

  protected def createKeySerializer(topic: String, kafkaConfig: KafkaConfig): Serializer[K]

  protected def createValueSerializer(topic: String, kafkaConfig: KafkaConfig): Serializer[V]

  protected def extractKey(obj: T, topic: String): K

  protected def extractValue(obj: T, topic: String): V

  override def create(topic: String, kafkaConfig: KafkaConfig): KeyedSerializationSchema[T] = {
    new KeyedSerializationSchema[T] {
      private lazy val keySerializer = createKeySerializer(topic, kafkaConfig)
      private lazy val valueSerializer = createValueSerializer(topic, kafkaConfig)

      override def serializeKey(element: T): Array[Byte] = {
        val key = extractKey(element, topic)
        keySerializer.serialize(topic, key)
      }

      override def serializeValue(element: T): Array[Byte] = {
        val value = extractValue(element, topic)
        valueSerializer.serialize(topic, value)
      }

      override def getTargetTopic(element: T): String = null
    }
  }

}