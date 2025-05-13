package pl.touk.nussknacker.engine.schemedkafka.serialization

import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.{Deserializer, StringDeserializer}
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.consumerrecord.ConsumerRecordKafkaDeserializationSchema
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchema
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData

import scala.reflect.ClassTag

/**
  * Abstract base implementation of [[KafkaSchemaBasedDeserializationSchemaFactory]]
  * which uses Kafka's Deserializer in returned Flink's KeyedDeserializationSchema. It deserializes both key and value
  * and wrap it in ConsumerRecord object (transforms raw event represented as ConsumerRecord from Array[Byte] domain to Key-Value-type domain).
  */
abstract class KafkaSchemaBasedKeyValueDeserializationSchemaFactory
    extends KafkaSchemaBasedDeserializationSchemaFactory {

  protected def createKeyOrUseStringDeserializer[K: ClassTag](
      schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      kafkaConfig: KafkaConfig
  ): Deserializer[K] = {
    if (kafkaConfig.useStringForKey) {
      createStringKeyDeserializer.asInstanceOf[Deserializer[K]]
    } else {
      createKeyDeserializer[K](schemaDataOpt, kafkaConfig)
    }
  }

  protected def createKeyDeserializer[K: ClassTag](
      schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      kafkaConfig: KafkaConfig
  ): Deserializer[K]

  protected def createValueDeserializer[V: ClassTag](
      schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      kafkaConfig: KafkaConfig
  ): Deserializer[V]

  protected def createStringKeyDeserializer: Deserializer[_] = new StringDeserializer

  override def create[K: ClassTag, V: ClassTag](
      kafkaConfig: KafkaConfig,
      keySchemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      valueSchemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]]
  ): KafkaDeserializationSchema[ConsumerRecord[K, V]] = {

    new ConsumerRecordKafkaDeserializationSchema[K, V] {

      @transient
      override protected lazy val keyDeserializer: Deserializer[K] =
        createKeyOrUseStringDeserializer[K](keySchemaDataOpt, kafkaConfig)

      @transient
      override protected lazy val valueDeserializer: Deserializer[V] =
        createValueDeserializer[V](valueSchemaDataOpt, kafkaConfig)

    }

  }

}
