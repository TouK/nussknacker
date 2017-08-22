package pl.touk.nussknacker.engine.kafka

import java.nio.charset.StandardCharsets

import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.util.serialization.KeyedSerializationSchema
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.{MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSink
import pl.touk.nussknacker.engine.kafka.KafkaSinkFactory._

class KafkaSinkFactory(config: KafkaConfig,
                       serializationSchema: KeyedSerializationSchema[Any]) extends SinkFactory {

  @MethodToInvoke
  def create(processMetaData: MetaData, @ParamName(`TopicParamName`) topic: String): Sink = {
    new FlinkSink with Serializable {
      override def toFlinkFunction: SinkFunction[Any] = {
        PartitionByKeyFlinkKafkaProducer09(config.kafkaAddress, topic, serializationSchema, config.kafkaProperties)
      }
      override def testDataOutput: Option[(Any) => String] = Option(value => new String(serializationSchema.serializeValue(value), StandardCharsets.UTF_8))
    }
  }
}

object KafkaSinkFactory {

  final val TopicParamName = "topic"

}
