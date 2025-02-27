package pl.touk.nussknacker.engine.process.registrar

import org.apache.flink.api.common.functions.RuntimeContext
import pl.touk.nussknacker.engine.api.{LazyParameter, ToEvaluateFunctionConverter}
import pl.touk.nussknacker.engine.api.LazyParameter.Evaluate
import pl.touk.nussknacker.engine.compile.nodecompilation.DefaultToEvaluateFunctionConverter
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerData

class ToEvaluateFunctionConverterWithLifecycle(runtimeContext: RuntimeContext, compilerData: FlinkProcessCompilerData)
    extends ToEvaluateFunctionConverter
    with AutoCloseable {

  compilerData.open(runtimeContext, nodesToUse = Nil)

  private val delegate = new DefaultToEvaluateFunctionConverter(compilerData.lazyParameterDeps)

  override def close(): Unit = {
    compilerData.close(Nil)
  }

  override def toEvaluateFunction[T <: AnyRef](parameter: LazyParameter[T]): Evaluate[T] =
    delegate.toEvaluateFunction[T](parameter)

}
