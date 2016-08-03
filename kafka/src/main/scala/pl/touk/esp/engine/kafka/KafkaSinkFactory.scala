package pl.touk.esp.engine.kafka

import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer09
import org.apache.flink.streaming.util.serialization.SerializationSchema
import pl.touk.esp.engine.api.process.{InputWithExectutionContext, Sink, SinkFactory}
import pl.touk.esp.engine.api.MetaData

class KafkaSinkFactory(kafkaAddress: String, serializationSchema: SerializationSchema[InputWithExectutionContext]) extends SinkFactory {
  override def create(processMetaData: MetaData, parameters: Map[String, String]): Sink = {
    val topic = parameters(KafkaSinkFactory.TopicParamName)
    
    new Sink {
      override def toFlinkSink: SinkFunction[InputWithExectutionContext] = {
        new FlinkKafkaProducer09(kafkaAddress, topic, serializationSchema)
      }
    }

  }
}

object KafkaSinkFactory {
  val TopicParamName = "topic"
}
