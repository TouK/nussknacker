package pl.touk.nussknacker.engine.kafka.generic

import java.lang
import java.nio.charset.StandardCharsets
import java.util.UUID

import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema
import org.apache.kafka.clients.producer.ProducerRecord
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSinkFactory}
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

object sinks {

  class GenericKafkaJsonSink(kafkaConfig: KafkaConfig) extends KafkaSinkFactory(kafkaConfig, GenericJsonSerialization)

  case class GenericJsonSerialization(topic: String) extends KafkaSerializationSchema[Any] {
    val encoder = BestEffortJsonEncoder(failOnUnkown = false)


    override def serialize(element: Any, timestamp: lang.Long): ProducerRecord[Array[Byte], Array[Byte]] = {
      val objToEncode = element match {
              // TODO: would be safer if will be added expected type in Sink and during expression evaluation,
              // would be performed conversion to it
              case TypedMap(fields) => fields
              case other => other
            }
      val encoded = encoder.encode(objToEncode).spaces2.getBytes(StandardCharsets.UTF_8)
      new ProducerRecord[Array[Byte], Array[Byte]](topic, UUID.randomUUID().toString.getBytes(StandardCharsets.UTF_8), encoded)
    }

  }

}
