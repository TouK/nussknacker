package pl.touk.esp.engine.process.api

import org.apache.flink.api.common.functions.RichFunction
import org.apache.flink.configuration.Configuration
import pl.touk.esp.engine.api.exception.EspExceptionHandler
import pl.touk.esp.engine.flink.api.exception.FlinkEspExceptionHandler

//wydzielic do flink-api?
trait WithExceptionHandler extends RichFunction {
  @transient lazy val exceptionHandler = lazyHandler()

  def lazyHandler: () => FlinkEspExceptionHandler

  override def close() = {
    exceptionHandler.close()
  }

  override def open(parameters: Configuration) = {
    exceptionHandler.open(getRuntimeContext)
  }
}