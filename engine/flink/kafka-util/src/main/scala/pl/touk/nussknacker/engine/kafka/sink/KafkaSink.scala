package pl.touk.nussknacker.engine.kafka.sink

import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.process.{BasicFlinkSink, FlinkLazyParameterFunctionHelper}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PartitionByKeyFlinkKafkaProducer}

class KafkaSink(topic: String, value: LazyParameter[AnyRef], kafkaConfig: KafkaConfig, serializationSchema: KafkaSerializationSchema[AnyRef], clientId: String)
  extends BasicFlinkSink[AnyRef] with Serializable {

  override def valueFunction(helper: FlinkLazyParameterFunctionHelper): FlatMapFunction[Context, ValueWithContext[AnyRef]] =
    helper.lazyMapFunction(value)

  override def toFlinkFunction: SinkFunction[AnyRef] = PartitionByKeyFlinkKafkaProducer(kafkaConfig, topic, serializationSchema, clientId)

}
