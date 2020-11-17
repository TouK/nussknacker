package pl.touk.nussknacker.engine.process

import org.apache.flink.api.common.functions.RichFunction
import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.flink.api.exception.FlinkEspExceptionHandler
import pl.touk.nussknacker.engine.process.compiler.CompiledProcessWithDeps

trait WithCompiledProcessDeps extends RichFunction {

  def compiledProcessWithDepsProvider: ClassLoader => CompiledProcessWithDeps

  protected lazy val compiledProcessWithDeps : CompiledProcessWithDeps = compiledProcessWithDepsProvider(getRuntimeContext.getUserCodeClassLoader)
  protected var exceptionHandler: FlinkEspExceptionHandler = _

  override def close(): Unit = {
    if (compiledProcessWithDeps != null) {
      compiledProcessWithDeps.close()
    }
    if (exceptionHandler != null) {
      exceptionHandler.close()
    }
  }

  override def open(parameters: Configuration): Unit = {
    compiledProcessWithDeps.open(getRuntimeContext)
    exceptionHandler = compiledProcessWithDeps.prepareExceptionHandler(getRuntimeContext)
  }


}
