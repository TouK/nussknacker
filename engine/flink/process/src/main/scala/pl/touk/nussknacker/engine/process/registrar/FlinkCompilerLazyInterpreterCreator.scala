package pl.touk.nussknacker.engine.process.registrar

import org.apache.flink.api.common.functions.RuntimeContext
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.exception.EspExceptionHandler
import pl.touk.nussknacker.engine.definition.{CompilerLazyParameterInterpreter, LazyInterpreterDependencies}
import pl.touk.nussknacker.engine.process.compiler.CompiledProcessWithDeps

class FlinkCompilerLazyInterpreterCreator(runtimeContext: RuntimeContext, withDeps: CompiledProcessWithDeps)
  extends CompilerLazyParameterInterpreter {

  //TODO: is this good place?
  withDeps.open(runtimeContext)

  val deps: LazyInterpreterDependencies = withDeps.lazyInterpreterDeps

  val metaData: MetaData = withDeps.metaData

  override def close(): Unit = {
    withDeps.close()
  }

  override def exceptionHandler: EspExceptionHandler = {
    withDeps.exceptionHandler
  }
}
