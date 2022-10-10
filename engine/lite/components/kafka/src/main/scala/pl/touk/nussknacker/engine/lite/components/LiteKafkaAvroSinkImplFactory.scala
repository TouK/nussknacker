package pl.touk.nussknacker.engine.lite.components

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.process.Sink
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.sink.KafkaAvroSinkImplFactory
import pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchema
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PreparedKafkaTopic}
import pl.touk.nussknacker.engine.util.KeyedValue

object LiteKafkaAvroSinkImplFactory extends KafkaAvroSinkImplFactory {
  override def createSink(preparedTopic: PreparedKafkaTopic, keyParam: LazyParameter[AnyRef], valueParam: LazyParameter[AnyRef],
                          kafkaConfig: KafkaConfig, serializationSchema: KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]], clientId: String,
                          schema: RuntimeSchemaData[AvroSchema], validationMode: ValidationMode): Sink =
    LiteKafkaUniversalSinkImplFactory.createSink(preparedTopic, keyParam, valueParam, kafkaConfig, serializationSchema, clientId, schema.toParsedSchemaData, validationMode)
}
