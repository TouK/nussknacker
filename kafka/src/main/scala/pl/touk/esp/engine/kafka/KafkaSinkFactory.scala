package pl.touk.esp.engine.kafka

import java.util.Properties

import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer09
import org.apache.flink.streaming.util.serialization.KeyedSerializationSchema
import pl.touk.esp.engine.api.{MetaData, ParamName}
import pl.touk.esp.engine.api.process.{Sink, SinkFactory}
import KafkaSinkFactory._
import pl.touk.esp.engine.flink.api.process.{FlinkSink, FlinkSourceFactory}

import collection.JavaConverters._

class KafkaSinkFactory(config: KafkaConfig,
                       serializationSchema: KeyedSerializationSchema[Any]) extends SinkFactory {

  def create(processMetaData: MetaData, @ParamName(`TopicParamName`) topic: String): Sink = {
    new FlinkSink {
      //we give null as partitioner to use default kafka partition behaviour... 
      //default behaviour should partition by key
      override def toFlinkFunction: SinkFunction[Any] = new FlinkKafkaProducer09(
        topic, serializationSchema, kafkaProperties(), null)
    }
  }

  private def kafkaProperties(): Properties = {
    val props = new Properties()
    props.setProperty("bootstrap.servers", config.kafkaAddress)
    config.kafkaProperties.map(_.asJava).foreach(props.putAll)
    props
  }
}

object KafkaSinkFactory {

  final val TopicParamName = "topic"

}

