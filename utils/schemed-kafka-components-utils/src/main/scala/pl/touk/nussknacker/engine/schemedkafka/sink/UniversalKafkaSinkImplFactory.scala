package pl.touk.nussknacker.engine.schemedkafka.sink

import io.confluent.kafka.schemaregistry.ParsedSchema
import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.process.{Sink, TopicName}
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchema
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PreparedKafkaTopic}
import pl.touk.nussknacker.engine.util.KeyedValue

trait UniversalKafkaSinkImplFactory {

  def createSink(
      preparedTopic: PreparedKafkaTopic[TopicName.ForSink],
      key: LazyParameter[AnyRef],
      value: LazyParameter[AnyRef],
      kafkaConfig: KafkaConfig,
      serializationSchema: KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]],
      clientId: String,
      schema: RuntimeSchemaData[ParsedSchema],
      validationMode: ValidationMode
  ): Sink

}
