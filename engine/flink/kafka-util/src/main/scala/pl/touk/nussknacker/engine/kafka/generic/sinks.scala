package pl.touk.nussknacker.engine.kafka.generic

import java.nio.charset.StandardCharsets
import java.util.UUID

import org.apache.flink.streaming.util.serialization.KeyedSerializationSchema
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSinkFactory}
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

object sinks {

  class GenericKafkaJsonSink(kafkaConfig: KafkaConfig) extends KafkaSinkFactory(kafkaConfig, GenericJsonSerialization)

  object GenericJsonSerialization extends KeyedSerializationSchema[Any] {
    //TODO: handle embedded?
    val encoder = BestEffortJsonEncoder(failOnUnkown = false)
    override def serializeKey(element: Any): Array[Byte] = UUID.randomUUID().toString.getBytes(StandardCharsets.UTF_8)
    override def serializeValue(element: Any): Array[Byte] = encoder.encode(element).spaces2.getBytes()
    override def getTargetTopic(element: Any): String = null
  }


}
