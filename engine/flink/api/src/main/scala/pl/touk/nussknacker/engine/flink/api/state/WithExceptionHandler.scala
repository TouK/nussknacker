package pl.touk.nussknacker.engine.flink.api.state

import org.apache.flink.api.common.functions.RichFunction
import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.flink.api.exception.FlinkEspExceptionHandler

trait WithExceptionHandler extends RichFunction {

  @transient lazy val exceptionHandler = lazyHandler(getRuntimeContext.getUserCodeClassLoader)

  def lazyHandler: (ClassLoader) => FlinkEspExceptionHandler

  override def close() = {
    exceptionHandler.close()
  }

  override def open(parameters: Configuration) = {
    exceptionHandler.open(getRuntimeContext)
  }
}