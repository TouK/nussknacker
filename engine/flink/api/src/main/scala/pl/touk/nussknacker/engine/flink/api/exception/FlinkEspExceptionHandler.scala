package pl.touk.nussknacker.engine.flink.api.exception

import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.api.common.restartstrategy.RestartStrategies.RestartStrategyConfiguration
import pl.touk.nussknacker.engine.api.exception.{EspExceptionHandler, EspExceptionInfo}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext

object FlinkEspExceptionHandler {

  val empty: FlinkEspExceptionHandler = new FlinkEspExceptionHandler {
    override def restartStrategy: RestartStrategyConfiguration = RestartStrategies.noRestart()
    override def handle(exceptionInfo: EspExceptionInfo[_ <: Throwable]): Unit = ()
  }
}

trait FlinkEspExceptionHandler extends EspExceptionHandler {
  
  /**
    * Be aware that it is invoked prior to opening exception handler.
    */
  def restartStrategy: RestartStrategyConfiguration

}

abstract class DelegatingFlinkEspExceptionHandler(protected val delegate: FlinkEspExceptionHandler) extends FlinkEspExceptionHandler {

  override def open(context: EngineRuntimeContext): Unit = delegate.open(context)

  override def close(): Unit = delegate.close()

  override def restartStrategy: RestartStrategyConfiguration = delegate.restartStrategy
}
