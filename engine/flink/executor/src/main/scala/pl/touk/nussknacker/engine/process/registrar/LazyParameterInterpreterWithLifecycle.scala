package pl.touk.nussknacker.engine.process.registrar

import org.apache.flink.api.common.functions.RuntimeContext
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, LazyParameterInterpreter}
import pl.touk.nussknacker.engine.compile.nodecompilation.DefaultLazyParameterInterpreter
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerData

class LazyParameterInterpreterWithLifecycle(runtimeContext: RuntimeContext, compilerData: FlinkProcessCompilerData)
    extends LazyParameterInterpreter
    with AutoCloseable {

  compilerData.open(runtimeContext, nodesToUse = Nil)

  private val delegate = new DefaultLazyParameterInterpreter(compilerData.lazyParameterDeps)

  override def close(): Unit = {
    compilerData.close(Nil)
  }

  override def syncInterpretationFunction[T <: AnyRef](parameter: LazyParameter[T]): Context => T =
    delegate.syncInterpretationFunction[T](parameter)

}
