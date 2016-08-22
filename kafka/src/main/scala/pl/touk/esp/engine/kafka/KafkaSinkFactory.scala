package pl.touk.esp.engine.kafka

import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer09
import org.apache.flink.streaming.util.serialization.{KeyedSerializationSchema, SerializationSchema}
import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.api.process.{Sink, SinkFactory}

class KafkaSinkFactory(kafkaAddress: String,
                       serializationSchema: KeyedSerializationSchema[Any]) extends SinkFactory {

  override def create(processMetaData: MetaData, parameters: Map[String, String]): Sink = {
    val topic = parameters(KafkaSinkFactory.TopicParamName)
    new Sink {
      override def toFlinkFunction: SinkFunction[Any] = new FlinkKafkaProducer09(kafkaAddress, topic, serializationSchema)
    }
  }
}

object KafkaSinkFactory {
  val TopicParamName = "topic"
}
