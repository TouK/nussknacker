package pl.touk.nussknacker.engine.schemedkafka.serialization

import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.{Deserializer, StringDeserializer}
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.consumerrecord.ConsumerRecordKafkaDeserializationSchema
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchema
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData

abstract class KafkaSchemaBasedKeyValueDeserializationSchemaFactory extends Serializable {

  protected def createKeyOrUseStringDeserializer[K](
      schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      kafkaConfig: KafkaConfig
  ): Deserializer[K] = {
    if (kafkaConfig.useStringForKey) {
      createStringKeyDeserializer.asInstanceOf[Deserializer[K]]
    } else {
      createKeyDeserializer[K](schemaDataOpt, kafkaConfig)
    }
  }

  protected def createKeyDeserializer[K](
      schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      kafkaConfig: KafkaConfig
  ): Deserializer[K]

  protected def createValueDeserializer[V](
      schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      kafkaConfig: KafkaConfig
  ): Deserializer[V]

  protected def createStringKeyDeserializer: Deserializer[_] = new StringDeserializer

  def create[K, V](
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
