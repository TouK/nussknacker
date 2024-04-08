package pl.touk.nussknacker.engine.process.registrar

import org.apache.flink.api.common.functions.RuntimeContext
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.compile.nodecompilation.{
  CompilerLazyParameterInterpreter,
  LazyInterpreterDependencies
}
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerData

class FlinkCompilerLazyInterpreterCreator(
    runtimeContext: RuntimeContext,
    compilerData: FlinkProcessCompilerData
) extends CompilerLazyParameterInterpreter {

  // TODO: is this good place?
  compilerData.open(runtimeContext, Nil)

  val deps: LazyInterpreterDependencies = compilerData.lazyInterpreterDeps

  val metaData: MetaData = compilerData.metaData

  override def close(): Unit = {
    compilerData.close(Nil)
  }

}
