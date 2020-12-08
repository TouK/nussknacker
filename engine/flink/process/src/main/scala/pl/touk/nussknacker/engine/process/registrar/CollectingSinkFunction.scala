package pl.touk.nussknacker.engine.process.registrar

import org.apache.flink.streaming.api.functions.sink.{RichSinkFunction, SinkFunction}
import pl.touk.nussknacker.engine.api.InterpretationResult
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.SinkInvocationCollector
import pl.touk.nussknacker.engine.compiledgraph.part.SinkPart
import pl.touk.nussknacker.engine.process.WithCompiledProcessDeps
import pl.touk.nussknacker.engine.process.compiler.CompiledProcessWithDeps

private[registrar] class CollectingSinkFunction(val compiledProcessWithDepsProvider: ClassLoader => CompiledProcessWithDeps,
                                                collectingSink: SinkInvocationCollector, sink: SinkPart)
  extends RichSinkFunction[InterpretationResult] with WithCompiledProcessDeps {

  override def invoke(value: InterpretationResult, context: SinkFunction.Context[_]): Unit = {
    exceptionHandler.handling(Some(sink.id), value.finalContext) {
      collectingSink.collect(value)
    }
  }
}
