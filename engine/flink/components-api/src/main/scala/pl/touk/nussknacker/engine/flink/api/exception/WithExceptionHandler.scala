package pl.touk.nussknacker.engine.flink.api.exception

import org.apache.flink.api.common.functions.{OpenContext, RichFunction, RuntimeContext}

/**
  * Helper for using exception handler.
  *
  * Be aware that super.open and super.close are not called.
  */
trait WithExceptionHandler {
  self: RichFunction =>

  protected def exceptionHandlerPreparer: RuntimeContext => ExceptionHandler

  protected var exceptionHandler: ExceptionHandler = _

  override def open(openContext: OpenContext): Unit = {
    exceptionHandler = exceptionHandlerPreparer(getRuntimeContext)
  }

  override def close(): Unit = {
    if (exceptionHandler != null) {
      exceptionHandler.close()
    }
  }

}
