package pl.touk.nussknacker.engine.schemedkafka.serialization

import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.{Deserializer, StringDeserializer}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.kafka.KafkaComponentsConfig
import pl.touk.nussknacker.engine.kafka.consumerrecord.ConsumerRecordKafkaDeserializationSchema
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchema
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData

abstract class KafkaSchemaBasedKeyValueDeserializationSchemaFactory extends Serializable {

  protected def kafkaComponentsConfig: KafkaComponentsConfig

  private def createKeyOrUseStringDeserializer[K](
      schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
  ): Deserializer[K] = {
    if (kafkaComponentsConfig.useStringForKey) {
      createStringKeyDeserializer.asInstanceOf[Deserializer[K]]
    } else {
      createKeyDeserializer[K](schemaDataOpt)
    }
  }

  protected def createKeyDeserializer[K](
      schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
  ): Deserializer[K]

  protected def createValueDeserializer[V](
      schemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      dataSampleTypingResult: Option[TypingResult]
  ): Deserializer[V]

  protected def createStringKeyDeserializer: Deserializer[_] = new StringDeserializer

  def create[K, V](
      keySchemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      valueSchemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      dataSampleTypingResult: Option[TypingResult]
  ): KafkaDeserializationSchema[ConsumerRecord[K, V]] = {

    new ConsumerRecordKafkaDeserializationSchema[K, V] {

      @transient
      override protected lazy val keyDeserializer: Deserializer[K] =
        createKeyOrUseStringDeserializer[K](keySchemaDataOpt)

      @transient
      override protected lazy val valueDeserializer: Deserializer[V] =
        createValueDeserializer[V](valueSchemaDataOpt, dataSampleTypingResult)

    }

  }

}
