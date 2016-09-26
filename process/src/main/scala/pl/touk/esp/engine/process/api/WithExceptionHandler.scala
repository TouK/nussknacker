package pl.touk.esp.engine.process.api

import org.apache.flink.api.common.functions.RichFunction
import org.apache.flink.configuration.Configuration
import pl.touk.esp.engine.api.exception.EspExceptionHandler

//wydzielic do flink-api?
trait WithExceptionHandler extends RichFunction {
  @transient lazy val exceptionHandler = lazyHandler()

  def lazyHandler: () => EspExceptionHandler

  override def close() = {
    exceptionHandler.close()
  }

  override def open(parameters: Configuration) = {
    exceptionHandler.open(getRuntimeContext)
  }
}