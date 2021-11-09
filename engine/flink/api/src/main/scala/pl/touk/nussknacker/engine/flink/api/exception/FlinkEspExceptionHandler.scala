package pl.touk.nussknacker.engine.flink.api.exception

import pl.touk.nussknacker.engine.api.exception.EspExceptionHandler
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext

trait FlinkEspExceptionHandler extends EspExceptionHandler

abstract class DelegatingFlinkEspExceptionHandler(protected val delegate: FlinkEspExceptionHandler) extends FlinkEspExceptionHandler {

  override def open(context: EngineRuntimeContext): Unit = delegate.open(context)

  override def close(): Unit = delegate.close()
}
