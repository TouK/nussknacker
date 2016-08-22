package pl.touk.esp.engine.kafka

import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer09
import org.apache.flink.streaming.util.serialization.KeyedSerializationSchema
import pl.touk.esp.engine.api.{MetaData, ParamName}
import pl.touk.esp.engine.api.process.{Sink, SinkFactory}
import KafkaSinkFactory._

class KafkaSinkFactory(kafkaAddress: String,
                       serializationSchema: KeyedSerializationSchema[Any]) extends SinkFactory {

  def create(processMetaData: MetaData, @ParamName(`TopicParamName`) topic: String): Sink = {
    new Sink {
      override def toFlinkFunction: SinkFunction[Any] = new FlinkKafkaProducer09(kafkaAddress, topic, serializationSchema)
    }
  }
}

object KafkaSinkFactory {

  final val TopicParamName = "topic"

}
