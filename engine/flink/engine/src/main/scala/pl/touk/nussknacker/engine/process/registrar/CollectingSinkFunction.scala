package pl.touk.nussknacker.engine.process.registrar

import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.streaming.api.functions.sink.{RichSinkFunction, SinkFunction}
import pl.touk.nussknacker.engine.api.{InterpretationResult, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.exception.FlinkEspExceptionHandler
import pl.touk.nussknacker.engine.process.ExceptionHandlerFunction
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerData
import pl.touk.nussknacker.engine.testmode.SinkInvocationCollector

private[registrar] class CollectingSinkFunction[T](val compiledProcessWithDepsProvider: ClassLoader => FlinkProcessCompilerData,
//                                                   override val exceptionHandlerPreparer: RuntimeContext => FlinkEspExceptionHandler,
                                                   collectingSink: SinkInvocationCollector, sinkId: String)
  extends RichSinkFunction[ValueWithContext[T]] with ExceptionHandlerFunction {

  override def invoke(value: ValueWithContext[T], context: SinkFunction.Context): Unit = {
    exceptionHandler.handling(Some(sinkId), value.context) {
      collectingSink.collect(value.context, value.value)
    }
  }
}
