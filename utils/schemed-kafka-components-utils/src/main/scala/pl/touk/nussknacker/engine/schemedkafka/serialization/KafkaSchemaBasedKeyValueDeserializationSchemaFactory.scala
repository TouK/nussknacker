package pl.touk.nussknacker.engine.schemedkafka.serialization

import com.github.ghik.silencer.silent
import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.{Deserializer, StringDeserializer}
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchema

import scala.reflect.ClassTag

/**
  * Abstract base implementation of [[KafkaSchemaBasedDeserializationSchemaFactory]]
  * which uses Kafka's Deserializer in returned Flink's KeyedDeserializationSchema. It deserializes both key and value
  * and wrap it in ConsumerRecord object (transforms raw event represented as ConsumerRecord from Array[Byte] domain to Key-Value-type domain).
  */
abstract class KafkaSchemaBasedKeyValueDeserializationSchemaFactory
  extends KafkaSchemaBasedDeserializationSchemaFactory {

  protected def createKeyDeserializer[K: ClassTag](schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]], kafkaConfig: KafkaConfig): Deserializer[K]

  protected def createValueDeserializer[V: ClassTag](schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]], kafkaConfig: KafkaConfig): Deserializer[V]

  protected def createStringKeyDeserializer: Deserializer[_] = new StringDeserializer

  override def create[K: ClassTag, V: ClassTag](kafkaConfig: KafkaConfig,
                                                keySchemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
                                                valueSchemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]]
                                               ): KafkaDeserializationSchema[ConsumerRecord[K, V]] = {

    new KafkaDeserializationSchema[ConsumerRecord[K, V]] {

      @transient
      private lazy val keyDeserializer = if (kafkaConfig.useStringForKey) {
        createStringKeyDeserializer.asInstanceOf[Deserializer[K]]
      } else {
        createKeyDeserializer[K](keySchemaDataOpt, kafkaConfig)
      }
      @transient
      private lazy val valueDeserializer = createValueDeserializer[V](valueSchemaDataOpt, kafkaConfig)

      @silent("deprecated") //using deprecated constructor for Flink 1.14/15 compatibility
      override def deserialize(record: ConsumerRecord[Array[Byte], Array[Byte]]): ConsumerRecord[K, V] = {
        val key = keyDeserializer.deserialize(record.topic(), record.headers(), record.key())
        val value = valueDeserializer.deserialize(record.topic(), record.headers(), record.value())
        new ConsumerRecord[K, V](
          record.topic(),
          record.partition(),
          record.offset(),
          record.timestamp(),
          record.timestampType(),
          ConsumerRecord.NULL_CHECKSUM.longValue(),
          record.serializedKeySize(),
          record.serializedValueSize(),
          key,
          value,
          record.headers(),
          record.leaderEpoch()
        )
      }

      override def isEndOfStream(nextElement: ConsumerRecord[K, V]): Boolean = false

    }
  }

}
