package pl.touk.nussknacker.engine.kafka.serialization.flink

import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchema

trait KafkaFlinkDeserializationSchema[T] extends KafkaDeserializationSchema[T]
