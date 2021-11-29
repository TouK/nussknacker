package pl.touk.nussknacker.engine.lite.components

import org.apache.kafka.clients.producer.ProducerRecord
import pl.touk.nussknacker.engine.api.process.Sink
import pl.touk.nussknacker.engine.api.{LazyParameter, LazyParameterInterpreter}
import pl.touk.nussknacker.engine.avro.RuntimeSchemaData
import pl.touk.nussknacker.engine.avro.encode.{BestEffortAvroEncoder, ValidationMode}
import pl.touk.nussknacker.engine.avro.sink.KafkaAvroSinkImplFactory
import pl.touk.nussknacker.engine.lite.api.utils.sinks.LazyParamSink
import pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchema
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PreparedKafkaTopic}
import pl.touk.nussknacker.engine.util.KeyedValue

object LiteKafkaAvroSinkImplFactory extends KafkaAvroSinkImplFactory {
  override def createSink(preparedTopic: PreparedKafkaTopic, keyParam: LazyParameter[AnyRef], valueParam: LazyParameter[AnyRef],
                          kafkaConfig: KafkaConfig, serializationSchema: KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]], clientId: String,
                          schema: RuntimeSchemaData, validationMode: ValidationMode): Sink = {
    val avroEncoder = BestEffortAvroEncoder(validationMode)
    new LazyParamSink[ProducerRecord[Array[Byte], Array[Byte]]] {
      override def prepareResponse(implicit evaluateLazyParameter: LazyParameterInterpreter): LazyParameter[ProducerRecord[Array[Byte], Array[Byte]]] = {
        keyParam.product(valueParam).map {
          case (key, value) =>
            val transformedValue = avroEncoder.encodeOrError(value, schema.schema)
            // TODO: timestamp, override topic, clientId, what about other props from KafkaSink?
            serializationSchema.serialize(KeyedValue(key, transformedValue), System.currentTimeMillis())
        }
      }
    }
  }
}
