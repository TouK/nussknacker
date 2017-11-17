package pl.touk.nussknacker.engine.flink.api.exception

import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.api.common.restartstrategy.RestartStrategies.RestartStrategyConfiguration
import pl.touk.nussknacker.engine.api.exception.EspExceptionHandler

trait FlinkEspExceptionHandler extends EspExceptionHandler {

  def restartStrategy: RestartStrategyConfiguration

  def open(runtimeContext: RuntimeContext): Unit = {}
  def close(): Unit = {}

}
