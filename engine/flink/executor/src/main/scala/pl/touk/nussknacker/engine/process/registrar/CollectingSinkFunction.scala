package pl.touk.nussknacker.engine.process.registrar

import org.apache.flink.streaming.api.functions.sink.{RichSinkFunction, SinkFunction}
import pl.touk.nussknacker.engine.api.component.{NodeComponentInfo, RealComponentType}
import pl.touk.nussknacker.engine.api.ValueWithContext
import pl.touk.nussknacker.engine.process.ExceptionHandlerFunction
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerData
import pl.touk.nussknacker.engine.testmode.SinkInvocationCollector

private[registrar] class CollectingSinkFunction[T](
    val compiledProcessWithDepsProvider: ClassLoader => FlinkProcessCompilerData,
    collectingSink: SinkInvocationCollector,
    sinkId: String
) extends RichSinkFunction[ValueWithContext[T]]
    with ExceptionHandlerFunction {

  override def invoke(value: ValueWithContext[T], context: SinkFunction.Context): Unit = {
    exceptionHandler.handling(
      Some(NodeComponentInfo(sinkId, "collectingSinkFunction", RealComponentType.Sink)),
      value.context
    ) {
      collectingSink.collect(value.context, value.value)
    }
  }

}
