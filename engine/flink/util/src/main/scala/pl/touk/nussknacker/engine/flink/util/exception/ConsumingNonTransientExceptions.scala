package pl.touk.nussknacker.engine.flink.util.exception

import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionHandler}
import pl.touk.nussknacker.engine.util.exception.WithExceptionExtractor


trait ConsumingNonTransientExceptions extends FlinkEspExceptionHandler with WithExceptionExtractor {

  override def open(context: EngineRuntimeContext): Unit = {
    consumer.open(context)
  }

  override def close(): Unit = {
    consumer.close()
  }

  override def handle(exceptionInfo: EspExceptionInfo[_ <: Throwable]): Unit = {
    consumer.consume(extractOrThrow(exceptionInfo))
  }

  protected def consumer: FlinkEspExceptionConsumer

}