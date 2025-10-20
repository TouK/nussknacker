package pl.touk.nussknacker.engine.schemedkafka.sink.flink

import io.confluent.kafka.schemaregistry.ParsedSchema
import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{Sink, TopicName}
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.kafka.{KafkaComponentsConfig, PreparedKafkaTopic}
import pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchema
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.sink.UniversalKafkaSinkImplFactory
import pl.touk.nussknacker.engine.util.KeyedValue

object FlinkKafkaUniversalSinkImplFactory extends UniversalKafkaSinkImplFactory with Serializable {

  override def createSink(
      preparedTopic: PreparedKafkaTopic[TopicName.ForSink],
      key: LazyParameter[AnyRef],
      keyParameterName: ParameterName,
      value: LazyParameter[AnyRef],
      kafkaComponentsConfig: KafkaComponentsConfig,
      serializationSchema: KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]],
      clientId: String,
      schema: RuntimeSchemaData[ParsedSchema],
      validationMode: ValidationMode
  ): Sink = {
    new FlinkKafkaUniversalSink(
      preparedTopic,
      key,
      keyParameterName,
      value,
      kafkaComponentsConfig,
      serializationSchema,
      clientId,
      schema.serializableSchema,
      validationMode
    )
  }

}
