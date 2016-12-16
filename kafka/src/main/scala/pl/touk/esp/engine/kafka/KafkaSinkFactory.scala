package pl.touk.esp.engine.kafka

import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.util.serialization.KeyedSerializationSchema
import pl.touk.esp.engine.api.process.{Sink, SinkFactory}
import pl.touk.esp.engine.api.{MetaData, ParamName}
import pl.touk.esp.engine.flink.api.process.FlinkSink
import pl.touk.esp.engine.kafka.KafkaSinkFactory._

class KafkaSinkFactory(config: KafkaConfig,
                       serializationSchema: KeyedSerializationSchema[Any]) extends SinkFactory {

  def create(processMetaData: MetaData, @ParamName(`TopicParamName`) topic: String): Sink = {
    new FlinkSink {
      override def toFlinkFunction: SinkFunction[Any] = {
        PartitionByKeyFlinkKafkaProducer09(config.kafkaAddress, topic, serializationSchema, config.kafkaProperties)
      }
    }
  }
}

object KafkaSinkFactory {

  final val TopicParamName = "topic"

}

