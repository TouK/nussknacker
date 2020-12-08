package pl.touk.nussknacker.engine.flink.api.exception

import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.api.common.restartstrategy.RestartStrategies.RestartStrategyConfiguration
import pl.touk.nussknacker.engine.api.exception.EspExceptionHandler
import pl.touk.nussknacker.engine.flink.api.RuntimeContextLifecycle

trait FlinkEspExceptionHandler extends EspExceptionHandler with RuntimeContextLifecycle {

  /**
    * Be aware that it is invoked prior to opening exception handler.
    */
  def restartStrategy: RestartStrategyConfiguration

}

abstract class DelegatingFlinkEspExceptionHandler(protected val delegate: FlinkEspExceptionHandler) extends FlinkEspExceptionHandler {

  override def open(runtimeContext: RuntimeContext): Unit = delegate.open(runtimeContext)

  override def close(): Unit = delegate.close()

  override def restartStrategy: RestartStrategyConfiguration = delegate.restartStrategy
}
