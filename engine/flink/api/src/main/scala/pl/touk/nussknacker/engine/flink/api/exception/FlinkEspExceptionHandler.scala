package pl.touk.nussknacker.engine.flink.api.exception

import org.apache.flink.api.common.restartstrategy.RestartStrategies.RestartStrategyConfiguration
import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.api.exception.EspExceptionHandler
import pl.touk.nussknacker.engine.api.runtimecontext.{EngineRuntimeContext, EngineRuntimeContextLifecycle}

trait FlinkEspExceptionHandler extends EspExceptionHandler with EngineRuntimeContextLifecycle {
  
  /**
    * Be aware that it is invoked prior to opening exception handler.
    */
  def restartStrategy: RestartStrategyConfiguration

}

abstract class DelegatingFlinkEspExceptionHandler(protected val delegate: FlinkEspExceptionHandler) extends FlinkEspExceptionHandler {

  override def open(context: EngineRuntimeContext): Unit = delegate.open(context)

  override def open(jobData: JobData): Unit = delegate.open(jobData)

  override def close(): Unit = delegate.close()

  override def restartStrategy: RestartStrategyConfiguration = delegate.restartStrategy
}
