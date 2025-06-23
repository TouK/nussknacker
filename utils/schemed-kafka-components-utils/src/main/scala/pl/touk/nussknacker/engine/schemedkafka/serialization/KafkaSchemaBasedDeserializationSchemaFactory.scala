package pl.touk.nussknacker.engine.schemedkafka.serialization

import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchema
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData

/**
  * Factory class for Flink's KeyedDeserializationSchema. It is extracted for purpose when for creation
  * of KeyedDeserializationSchema are needed additional avro related information.
  */
trait KafkaSchemaBasedDeserializationSchemaFactory extends Serializable {

  /**
    * Prepare Flink's KafkaDeserializationSchema based on provided information.
    *
    * @param kafkaConfig        Configuration of integration with Kafka.
    * @param keySchemaDataOpt   Schema which will be used as a key reader schema.
    * @param valueSchemaDataOpt Schema which will be used as a value reader schema. In case of None, writer schema will be used.
    * @return KafkaDeserializationSchema
    */
  def create[K, V](
      kafkaConfig: KafkaConfig,
      keySchemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]],
      valueSchemaDataOpt: Option[RuntimeSchemaData[ParsedSchema]]
  ): KafkaDeserializationSchema[ConsumerRecord[K, V]]

}
