package pl.touk.nussknacker.engine.kafka.serialization

import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import java.lang

/**
  * Factory class for Flink's KeyedSerializationSchema. It is extracted for  purpose when for creation
  * of KeyedSerializationSchema are needed additional information like list of topics and configuration.
  *
  * @tparam T type of serialized object
  */
trait KafkaSerializationSchemaFactory[T] extends Serializable {
  def create(topic: String, kafkaConfig: KafkaConfig): KafkaSerializationSchema[T]
}

/**
  * Factory which always return the same schema.
  *
  * @param serializationSchema schema which will be returned.
  * @tparam T type of serialized object
  */
case class FixedKafkaSerializationSchemaFactory[T](serializationSchema: String => KafkaSerializationSchema[T])
  extends KafkaSerializationSchemaFactory[T] with Serializable {

  override def create(topic: String, kafkaConfig: KafkaConfig): KafkaSerializationSchema[T] = serializationSchema(topic)
}

/**
  * Abstract base implementation of [[pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchemaFactory]]
  * which uses Kafka's Serializer in returned Flink's KeyedSerializationSchema. It serializes only value - key will be
  * randomly generated as a UUID.
  *
  * @tparam T type of serialized object
  */
abstract class BaseKafkaSerializationSchemaFactory[T] extends KafkaSerializationSchemaFactory[T] with Serializable {

  protected def createValueSerializer(topic: String, kafkaConfig: KafkaConfig): Serializer[T]

  protected def createKeySerializer(topic: String, kafkaConfig: KafkaConfig): Serializer[T] = new UUIDSerializer[T]

  override def create(topic: String, kafkaConfig: KafkaConfig): KafkaSerializationSchema[T] = {
    new KafkaSerializationSchema[T] {

      private lazy val valueSerializer = createValueSerializer(topic, kafkaConfig)

      private lazy val keySerializer = createKeySerializer(topic, kafkaConfig)

      override def serialize(element: T, timestamp: lang.Long): ProducerRecord[Array[Byte], Array[Byte]] = {
        new ProducerRecord[Array[Byte], Array[Byte]](topic,
          keySerializer.serialize(topic, element),
          valueSerializer.serialize(topic, element)
        )
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
abstract class BaseKeyValueKafkaSerializationSchemaFactory[T] extends KafkaSerializationSchemaFactory[T] with Serializable {

  protected type K

  protected type V

  protected def createKeySerializer(topic: String, kafkaConfig: KafkaConfig): Serializer[K]

  protected def createValueSerializer(topic: String, kafkaConfig: KafkaConfig): Serializer[V]

  protected def extractKey(obj: T, topic: String): K

  protected def extractValue(obj: T, topic: String): V

  override def create(topic: String, kafkaConfig: KafkaConfig): KafkaSerializationSchema[T] = {
    new KafkaSerializationSchema[T] {
      private lazy val keySerializer = createKeySerializer(topic, kafkaConfig)
      private lazy val valueSerializer = createValueSerializer(topic, kafkaConfig)

      override def serialize(element: T, timestamp: lang.Long): ProducerRecord[Array[Byte], Array[Byte]] = {
        val key = extractKey(element, topic)
        val value = extractValue(element, topic)
        //Kafka timestamp has to be >= 0, while Flink can use Long.MinValue
        val timestampForKafka = Math.max(0, timestamp)

        new ProducerRecord[Array[Byte], Array[Byte]](topic, null, timestampForKafka,
          keySerializer.serialize(topic, key),
          valueSerializer.serialize(topic, value)
        )
      }
    }
  }
}
