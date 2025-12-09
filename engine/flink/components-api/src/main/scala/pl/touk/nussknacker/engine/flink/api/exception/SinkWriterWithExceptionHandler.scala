package pl.touk.nussknacker.engine.flink.api.exception

import org.apache.flink.api.connector.sink2.SinkWriter

trait SinkWriterWithExceptionHandler[T] extends AutoCloseable {
  self: SinkWriter[T] =>

  protected val exceptionHandler: ExceptionHandler

  override abstract def close(): Unit = {
    if (exceptionHandler != null) {
      exceptionHandler.close()
    }
    super.close()
  }

}
