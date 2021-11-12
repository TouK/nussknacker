package pl.touk.nussknacker.engine.kafka.generic

import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Sink}
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchema
import pl.touk.nussknacker.engine.kafka.sink.GenericJsonSerialization
import pl.touk.nussknacker.engine.kafka.sink.flink.{BaseKafkaSinkFactory, KafkaSink, KafkaSinkFactory}

//TODO: Move it to sink package
object sinks {

  trait FlinkKafkaSinkFactory {
    self: BaseKafkaSinkFactory =>
    override protected def prepareKafkaComponentImpl(topic: String, value: LazyParameter[AnyRef], kafkaConfig: KafkaConfig, serializationSchema: KafkaSerializationSchema[AnyRef], clientId: String): Sink =
      new KafkaSink(topic, value, kafkaConfig, serializationSchema, clientId)
  }

  class GenericKafkaJsonSinkFactory(processObjectDependencies: ProcessObjectDependencies)
    extends KafkaSinkFactory(GenericJsonSerialization(_), processObjectDependencies) with FlinkKafkaSinkFactory

}
