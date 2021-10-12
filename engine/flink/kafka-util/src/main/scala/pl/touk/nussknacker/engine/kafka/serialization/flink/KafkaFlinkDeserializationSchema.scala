package pl.touk.nussknacker.engine.kafka.serialization.flink

import org.apache.flink.api.java.typeutils.ResultTypeQueryable
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchema

trait KafkaFlinkDeserializationSchema[T] extends KafkaDeserializationSchema[T] with ResultTypeQueryable[T] {}
