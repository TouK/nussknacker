package pl.touk.nussknacker.engine.kafka.generic

import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Sink}
import pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchema
import pl.touk.nussknacker.engine.kafka.sink.flink.FlinkKafkaSink
import pl.touk.nussknacker.engine.kafka.sink.{GenericJsonSerialization, KafkaSinkFactory, KafkaSinkImplFactory}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PreparedKafkaTopic}

//TODO: Move it to sink package
object sinks {

  object FlinkKafkaSinkImplFactory extends KafkaSinkImplFactory {
    override def prepareSink(topic: PreparedKafkaTopic, value: LazyParameter[AnyRef], kafkaConfig: KafkaConfig,
                             serializationSchema: KafkaSerializationSchema[AnyRef], clientId: String): Sink =
      new FlinkKafkaSink(topic, value, kafkaConfig, serializationSchema, clientId)
  }

  class GenericKafkaJsonSinkFactory(processObjectDependencies: ProcessObjectDependencies)
    extends KafkaSinkFactory(GenericJsonSerialization(_), processObjectDependencies, FlinkKafkaSinkImplFactory)

}
