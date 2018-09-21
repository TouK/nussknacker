package pl.touk.nussknacker.engine.kafka.generic

import java.nio.charset.StandardCharsets
import java.util.UUID

import org.apache.flink.streaming.util.serialization.KeyedSerializationSchema
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSinkFactory}
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

object sinks {

  class GenericKafkaJsonSink(kafkaConfig: KafkaConfig) extends KafkaSinkFactory(kafkaConfig, GenericJsonSerialization)

  object GenericJsonSerialization extends KeyedSerializationSchema[Any] {
    val encoder = BestEffortJsonEncoder(failOnUnkown = false)
    override def serializeKey(element: Any): Array[Byte] = UUID.randomUUID().toString.getBytes(StandardCharsets.UTF_8)
    override def serializeValue(element: Any): Array[Byte] = {
      val objToEncode = element match {
        // TODO: would be safer if will be added expected type in Sink and during expression evaluation,
        // would be performed conversion to it
        case TypedMap(fields) => fields
        case other => other
      }
      encoder.encode(objToEncode).spaces2.getBytes()
    }
    override def getTargetTopic(element: Any): String = null
  }

}
