package pl.touk.nussknacker.engine.process.registrar

import org.apache.flink.streaming.api.functions.sink.{RichSinkFunction, SinkFunction}
import pl.touk.nussknacker.engine.api.InterpretationResult
import pl.touk.nussknacker.engine.process.ExceptionHandlerFunction
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerData
import pl.touk.nussknacker.engine.testmode.Collectors.SinkInvocationCollector

private[registrar] class CollectingSinkFunction(val compiledProcessWithDepsProvider: ClassLoader => FlinkProcessCompilerData,
                                                collectingSink: SinkInvocationCollector, sinkId: String)
  extends RichSinkFunction[InterpretationResult] with ExceptionHandlerFunction {

  override def invoke(value: InterpretationResult, context: SinkFunction.Context[_]): Unit = {
    exceptionHandler.handling(Some(sinkId), value.finalContext) {
      collectingSink.collect(value)
    }
  }
}
