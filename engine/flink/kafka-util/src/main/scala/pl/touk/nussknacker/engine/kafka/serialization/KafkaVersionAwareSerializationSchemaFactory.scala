package pl.touk.nussknacker.engine.kafka.serialization

import java.lang

import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.kafka.KafkaConfig

/**
  * Factory class for Flink's KeyedSerializationSchema. It is extracted for purpose when for creation
  * of KeyedSerializationSchema are needed additional information like list of topics, schema version and configuration.
  *
  * @tparam T type of serialized object
  */
trait KafkaVersionAwareSerializationSchemaFactory[T] extends KafkaSerializationSchemaFactory[T] {

  def create(topic: String, version: Option[Int], kafkaConfig: KafkaConfig): KafkaSerializationSchema[T]

  override def create(topic: String, kafkaConfig: KafkaConfig): KafkaSerializationSchema[T] = create(topic, None, kafkaConfig)
}

/**
  * Abstract base implementation of [[pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchemaFactory]]
  * which uses Kafka's Serializer in returned Flink's KeyedSerializationSchema. It serializes only value - key will be
  * randomly generated as a UUID.
  *
  * @tparam T type of serialized object
  */
abstract class KafkaVersionAwareValueSerializationSchemaFactory[T] extends KafkaVersionAwareSerializationSchemaFactory[T] {

  protected def createValueSerializer(topic: String, version: Option[Int], kafkaConfig: KafkaConfig): Serializer[T]

  protected def createKeySerializer(topic: String, version: Option[Int], kafkaConfig: KafkaConfig): Serializer[T] =
    new UUIDSerializer[T]

  override def create(topic: String, version: Option[Int], kafkaConfig: KafkaConfig): KafkaSerializationSchema[T] = {
    new KafkaSerializationSchema[T] {

      private lazy val valueSerializer = createValueSerializer(topic, version, kafkaConfig)
      private lazy val keySerializer = createKeySerializer(topic, version, kafkaConfig)

      override def serialize(element: T, timestamp: lang.Long): ProducerRecord[Array[Byte], Array[Byte]] = {
        KafkaProducerHelper.createRecord(topic,
          keySerializer.serialize(topic, element),
          valueSerializer.serialize(topic, element),
          timestamp)
      }
    }
  }
}

/**
  * Abstract base implementation of [[pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchemaFactory]]
  * which uses Kafka's Serializer in returned Flink's KeyedSerializationSchema. It serializes both key and value.
  *
  * @tparam T type of serialized object
  */
abstract class KafkaVersionAwareKeyValueSerializationSchemaFactory[T] extends KafkaVersionAwareSerializationSchemaFactory[T] {

  protected type K

  protected type V

  protected def createKeySerializer(topic: String, version: Option[Int], kafkaConfig: KafkaConfig): Serializer[K]

  protected def createValueSerializer(topic: String, version: Option[Int], kafkaConfig: KafkaConfig): Serializer[V]

  protected def extractKey(obj: T, version: Option[Int], topic: String): K

  protected def extractValue(obj: T, version: Option[Int], topic: String): V

  override def create(topic: String, version: Option[Int], kafkaConfig: KafkaConfig): KafkaSerializationSchema[T] = {
    new KafkaSerializationSchema[T] {
      private lazy val keySerializer = createKeySerializer(topic, version, kafkaConfig)
      private lazy val valueSerializer = createValueSerializer(topic, version, kafkaConfig)

      override def serialize(element: T, timestamp: lang.Long): ProducerRecord[Array[Byte], Array[Byte]] = {
        val key = keySerializer.serialize(topic, extractKey(element, version, topic))
        val value = valueSerializer.serialize(topic, extractValue(element, version, topic))
        KafkaProducerHelper.createRecord(topic, key, value, timestamp)
      }
    }
  }
}
