package pl.touk.nussknacker.engine.avro.sink.flink

import io.confluent.kafka.schemaregistry.ParsedSchema
import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.process.Sink
import pl.touk.nussknacker.engine.avro.RuntimeSchemaData
import pl.touk.nussknacker.engine.avro.encode.ValidationMode
import pl.touk.nussknacker.engine.avro.sink.UniversalKafkaSinkImplFactory
import pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchema
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PreparedKafkaTopic}
import pl.touk.nussknacker.engine.util.KeyedValue

object FlinkKafkaUniversalSinkImplFactory extends UniversalKafkaSinkImplFactory {

  def createSink(preparedTopic: PreparedKafkaTopic,
                 key: LazyParameter[AnyRef],
                 value: LazyParameter[AnyRef],
                 kafkaConfig: KafkaConfig,
                 serializationSchema: KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]],
                 clientId: String,
                 schema: RuntimeSchemaData[ParsedSchema],
                 validationMode: ValidationMode): Sink = {
    new FlinkKafkaUniversalSink(preparedTopic, key, value, kafkaConfig, serializationSchema, clientId, schema.serializableSchema, validationMode)
  }

}
