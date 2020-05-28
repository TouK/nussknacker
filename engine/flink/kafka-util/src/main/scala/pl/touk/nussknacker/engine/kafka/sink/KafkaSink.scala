package pl.touk.nussknacker.engine.kafka.sink

import java.nio.charset.StandardCharsets

import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema
import pl.touk.nussknacker.engine.flink.api.process.BasicFlinkSink
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PartitionByKeyFlinkKafkaProducer}

class KafkaSink(topic: String, kafkaConfig: KafkaConfig, serializationSchema: KafkaSerializationSchema[Any], clientId: String)
  extends BasicFlinkSink with Serializable {

  override def toFlinkFunction: SinkFunction[Any] =
    PartitionByKeyFlinkKafkaProducer(kafkaConfig, topic, serializationSchema, clientId)

  override def testDataOutput: Option[Any => String] = Option(value =>
    new String(serializationSchema.serialize(value, System.currentTimeMillis()).value(), StandardCharsets.UTF_8))
}
