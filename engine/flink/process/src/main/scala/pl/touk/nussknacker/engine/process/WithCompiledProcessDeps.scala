package pl.touk.nussknacker.engine.process

import org.apache.flink.api.common.functions.RichFunction
import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.process.compiler.CompiledProcessWithDeps

trait WithCompiledProcessDeps extends RichFunction {

  def compiledProcessWithDepsProvider: (ClassLoader) => CompiledProcessWithDeps

  protected lazy val compiledProcessWithDeps : CompiledProcessWithDeps = compiledProcessWithDepsProvider(getRuntimeContext.getUserCodeClassLoader)

  override def close(): Unit = {
    compiledProcessWithDeps.close()
  }

  override def open(parameters: Configuration): Unit = {
    compiledProcessWithDeps.open(getRuntimeContext)
  }


}
