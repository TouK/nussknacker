package pl.touk.nussknacker.engine.flink.api.exception

import org.apache.flink.api.connector.sink2.SinkWriter

trait SinkWriterWithExceptionHandler[T] {
  self: SinkWriter[T] =>

  protected val exceptionHandler: ExceptionHandler

  override def close(): Unit = {
    if (exceptionHandler != null) {
      exceptionHandler.close()
    }
  }

}
