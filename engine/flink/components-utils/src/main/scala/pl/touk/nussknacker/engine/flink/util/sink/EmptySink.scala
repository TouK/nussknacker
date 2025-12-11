package pl.touk.nussknacker.engine.flink.util.sink

import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.api.connector.sink2.Sink
import org.apache.flink.streaming.api.functions.sink.v2.DiscardingSink
import pl.touk.nussknacker.engine.api.{Context, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.process.{
  BasicFlinkSink,
  FlinkCustomNodeContext,
  FlinkLazyParameterFunctionHelper
}

object EmptySink extends EmptySink

trait EmptySink extends BasicFlinkSink {

  type Value = AnyRef

  override def valueFunction(
      helper: FlinkLazyParameterFunctionHelper
  ): FlatMapFunction[Context, ValueWithContext[AnyRef]] =
    (_, _) => {}

  override def toFlinkSink(flinkNodeContext: FlinkCustomNodeContext): Sink[AnyRef] =
    new DiscardingSink[AnyRef]

}
