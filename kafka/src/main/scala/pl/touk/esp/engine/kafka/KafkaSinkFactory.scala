package pl.touk.esp.engine.kafka

import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer09
import org.apache.flink.streaming.util.serialization.SerializationSchema
import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.api.process.{Sink, SinkFactory}

class KafkaSinkFactory(kafkaAddress: String,
                       serializationSchema: SerializationSchema[Any]) extends SinkFactory {

  override def create(processMetaData: MetaData, parameters: Map[String, String]): Sink = {
    val topic = parameters(KafkaSinkFactory.TopicParamName)
    Sink.fromFlinkSink(new FlinkKafkaProducer09(kafkaAddress, topic, serializationSchema))
  }
}

object KafkaSinkFactory {
  val TopicParamName = "topic"
}
