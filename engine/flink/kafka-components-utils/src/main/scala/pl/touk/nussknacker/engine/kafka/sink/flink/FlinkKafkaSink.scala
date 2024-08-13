package pl.touk.nussknacker.engine.kafka.sink.flink

import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.process.{BasicFlinkSink, FlinkLazyParameterFunctionHelper}
import pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchema
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PartitionByKeyFlinkKafkaProducer, PreparedKafkaTopic}

import java.nio.charset.StandardCharsets

// TODO: handle key passed by user - not only extracted by serialization schema from value
class FlinkKafkaSink(
    topic: PreparedKafkaTopic[TopicName.ForSink],
    value: LazyParameter[AnyRef],
    kafkaConfig: KafkaConfig,
    serializationSchema: KafkaSerializationSchema[AnyRef],
    clientId: String
) extends BasicFlinkSink
    with Serializable {

  type Value = AnyRef

  override def valueFunction(
      helper: FlinkLazyParameterFunctionHelper
  ): FlatMapFunction[Context, ValueWithContext[AnyRef]] =
    helper.lazyMapFunction(value)

  override def toFlinkFunction: SinkFunction[AnyRef] =
    PartitionByKeyFlinkKafkaProducer(kafkaConfig, topic.prepared, serializationSchema, clientId)

  override def prepareTestValueFunction: AnyRef => String =
    value =>
      new String(serializationSchema.serialize(value, System.currentTimeMillis()).value(), StandardCharsets.UTF_8)

}
