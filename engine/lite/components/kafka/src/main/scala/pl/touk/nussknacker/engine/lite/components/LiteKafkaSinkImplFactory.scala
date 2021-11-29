package pl.touk.nussknacker.engine.lite.components

import org.apache.kafka.clients.producer.ProducerRecord
import pl.touk.nussknacker.engine.api.process.Sink
import pl.touk.nussknacker.engine.api.{LazyParameter, LazyParameterInterpreter}
import pl.touk.nussknacker.engine.lite.api.utils.sinks.LazyParamSink
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PreparedKafkaTopic}
import pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchema
import pl.touk.nussknacker.engine.kafka.sink.KafkaSinkImplFactory

object LiteKafkaSinkImplFactory extends KafkaSinkImplFactory {
  override def prepareSink(topic: PreparedKafkaTopic, value: LazyParameter[AnyRef], kafkaConfig: KafkaConfig,
                           serializationSchema: KafkaSerializationSchema[AnyRef], clientId: String): Sink = {
    new LazyParamSink[ProducerRecord[Array[Byte], Array[Byte]]] {
      override def prepareResponse(implicit evaluateLazyParameter: LazyParameterInterpreter): LazyParameter[ProducerRecord[Array[Byte], Array[Byte]]] = {
        // TODO: timestamp, override topic, clientId, what about other props from KafkaSink?
        value.map(serializationSchema.serialize(_, System.currentTimeMillis()))
      }
    }
  }
}
