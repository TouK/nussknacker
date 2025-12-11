package pl.touk.nussknacker.engine.schemedkafka.sink.flink

import io.confluent.kafka.schemaregistry.ParsedSchema
import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.Sink
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.kafka.KafkaComponentsConfig
import pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchema
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.sink.UniversalKafkaSinkImplFactory
import pl.touk.nussknacker.engine.util.KeyedValue

object FlinkKafkaUniversalSinkImplFactory extends UniversalKafkaSinkImplFactory with Serializable {

  override def createSink(
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
