package pl.touk.nussknacker.engine.kafka.sink.flink

import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.api.connector.sink2.Sink
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.process.{
  BasicFlinkSink,
  FlinkCustomNodeContext,
  FlinkLazyParameterFunctionHelper
}
import pl.touk.nussknacker.engine.kafka.{KafkaComponentsConfig, PartitionByKeyFlinkKafkaSink}
import pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchema

import java.nio.charset.StandardCharsets

// TODO: handle key passed by user - not only extracted by serialization schema from value
class FlinkKafkaSink(
    value: LazyParameter[AnyRef],
    kafkaComponentsConfig: KafkaComponentsConfig,
    serializationSchema: KafkaSerializationSchema[AnyRef],
    clientId: String
) extends BasicFlinkSink
    with Serializable {

  type Value = AnyRef

  override def valueFunction(
      helper: FlinkLazyParameterFunctionHelper
  ): FlatMapFunction[Context, ValueWithContext[AnyRef]] =
    helper.lazyMapFunction(value)

  override def toFlinkSink(flinkNodeContext: FlinkCustomNodeContext): Sink[AnyRef] =
    PartitionByKeyFlinkKafkaSink(kafkaComponentsConfig, serializationSchema, clientId)

  override def prepareTestValueFunction: AnyRef => String =
    value =>
      new String(serializationSchema.serialize(value, System.currentTimeMillis()).value(), StandardCharsets.UTF_8)

}
