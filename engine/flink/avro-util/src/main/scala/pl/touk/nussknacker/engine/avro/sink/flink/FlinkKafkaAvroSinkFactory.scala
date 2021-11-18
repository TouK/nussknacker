package pl.touk.nussknacker.engine.avro.sink.flink

import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.process.Sink
import pl.touk.nussknacker.engine.avro.RuntimeSchemaData
import pl.touk.nussknacker.engine.avro.encode.ValidationMode
import pl.touk.nussknacker.engine.avro.sink.{AvroSinkValue, KafkaAvroSinkFactory}
import pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchema
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PreparedKafkaTopic}
import pl.touk.nussknacker.engine.util.KeyedValue

trait FlinkKafkaAvroSinkFactory extends KafkaAvroSinkFactory {

  protected def createSink(preparedTopic: PreparedKafkaTopic,
                           key: LazyParameter[AnyRef],
                           value: AvroSinkValue,
                           kafkaConfig: KafkaConfig,
                           serializationSchema: KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]],
                           schema: RuntimeSchemaData,
                           validationMode: ValidationMode,
                           clientId: String): Sink = {
    new KafkaAvroSink(preparedTopic, key, value, kafkaConfig, serializationSchema, schema.serializableSchema, clientId, validationMode)
  }

}
