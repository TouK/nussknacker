package pl.touk.nussknacker.engine.flink.util.sink

import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process.SinkFactory
import pl.touk.nussknacker.engine.flink.api.process.{
  BasicFlinkSink,
  FlinkCustomNodeContext,
  FlinkLazyParameterFunctionHelper,
  FlinkSink
}
import pl.touk.nussknacker.engine.flink.util.sink.SingleValueSinkFactory.SingleValueParamName

object SingleValueSinkFactory {
  final val SingleValueParamName = "Value"
}

class SingleValueSinkFactory[T <: AnyRef](sink: => SinkFunction[T]) extends SinkFactory with Serializable {

  @MethodToInvoke
  def invoke(@ParamName(`SingleValueParamName`) value: LazyParameter[T]): FlinkSink = {
    new BasicFlinkSink {

      type Value = T

      override def valueFunction(
          helper: FlinkLazyParameterFunctionHelper
      ): FlatMapFunction[Context, ValueWithContext[Value]] = helper.lazyMapFunction(value)

      override def toFlinkFunction(flinkNodeContext: FlinkCustomNodeContext): SinkFunction[Value] = sink
    }
  }

}
