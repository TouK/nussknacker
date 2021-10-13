package pl.touk.nussknacker.engine.flink.util.sink

import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import pl.touk.nussknacker.engine.api.process.SinkFactory
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.flink.api.process.{BasicFlinkSink, FlinkLazyParameterFunctionHelper, FlinkSink}

class SingleValueSinkFactory[T <: AnyRef](sink: => SinkFunction[T]) extends SinkFactory with Serializable {

  @MethodToInvoke
  def invoke(@ParamName("value") value: LazyParameter[T]): FlinkSink = {
    new BasicFlinkSink {

      type Value = T

      override def valueFunction(helper: FlinkLazyParameterFunctionHelper): FlatMapFunction[Context, ValueWithContext[Value]] = helper.lazyMapFunction(value)

      override def toFlinkFunction: SinkFunction[Value] = sink
    }
  }

}