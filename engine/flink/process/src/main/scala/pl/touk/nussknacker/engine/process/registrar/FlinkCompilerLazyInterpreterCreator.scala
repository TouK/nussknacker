package pl.touk.nussknacker.engine.process.registrar

import org.apache.flink.api.common.functions.RuntimeContext
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.definition.{CompilerLazyParameterInterpreter, LazyInterpreterDependencies}
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerData

class FlinkCompilerLazyInterpreterCreator(runtimeContext: RuntimeContext, withDeps: FlinkProcessCompilerData)
  extends CompilerLazyParameterInterpreter {

  //TODO: is this good place?
  withDeps.open(runtimeContext, Nil)

  val deps: LazyInterpreterDependencies = withDeps.lazyInterpreterDeps

  val metaData: MetaData = withDeps.metaData

  override def close(): Unit = {
    withDeps.close(Nil)
  }
}
