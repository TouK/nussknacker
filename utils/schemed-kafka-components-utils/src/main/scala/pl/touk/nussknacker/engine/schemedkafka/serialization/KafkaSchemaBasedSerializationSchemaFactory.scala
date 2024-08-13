package pl.touk.nussknacker.engine.schemedkafka.serialization

import io.confluent.kafka.schemaregistry.ParsedSchema
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, serialization}
import pl.touk.nussknacker.engine.util.KeyedValue

/**
  * Factory class for Flink's KeyedSerializationSchema. It is extracted for purpose when for creation
  * of KafkaSerializationSchema are needed additional avro related information. SerializationSchema will take
  * KafkaSerializationSchema with key extracted in the step before serialization
  */
trait KafkaSchemaBasedSerializationSchemaFactory extends Serializable {

  def create(
      topic: TopicName.ForSink,
      schemaOpt: Option[RuntimeSchemaData[ParsedSchema]],
      kafkaConfig: KafkaConfig
  ): serialization.KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]]

}
