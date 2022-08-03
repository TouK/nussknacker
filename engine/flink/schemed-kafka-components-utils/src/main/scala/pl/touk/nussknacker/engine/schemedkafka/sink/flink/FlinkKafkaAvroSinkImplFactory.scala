package pl.touk.nussknacker.engine.schemedkafka.sink.flink

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.flink.formats.avro.typeutils.NkSerializableParsedSchema
import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.process.Sink
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.encode.ValidationMode
import pl.touk.nussknacker.engine.schemedkafka.sink.KafkaAvroSinkImplFactory
import pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchema
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PreparedKafkaTopic}
import pl.touk.nussknacker.engine.util.KeyedValue

object FlinkKafkaAvroSinkImplFactory extends KafkaAvroSinkImplFactory {

  def createSink(preparedTopic: PreparedKafkaTopic,
                 key: LazyParameter[AnyRef],
                 value: LazyParameter[AnyRef],
                 kafkaConfig: KafkaConfig,
                 serializationSchema: KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]],
                 clientId: String,
                 schema: RuntimeSchemaData[AvroSchema],
                 validationMode: ValidationMode): Sink = {
    new FlinkKafkaUniversalSink(preparedTopic, key, value, kafkaConfig, serializationSchema, clientId, schema.serializableSchema.asInstanceOf[NkSerializableParsedSchema[ParsedSchema]], validationMode)
  }

}
