package pl.touk.nussknacker.engine.process.registrar

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.compile.nodecompilation.{
  CompilerLazyParameterInterpreter,
  LazyInterpreterDependencies
}
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerData

class FlinkCompilerLazyInterpreterCreator(compilerData: FlinkProcessCompilerData)
    extends CompilerLazyParameterInterpreter {

  val deps: LazyInterpreterDependencies = compilerData.lazyInterpreterDeps

  val metaData: MetaData = compilerData.metaData

}
