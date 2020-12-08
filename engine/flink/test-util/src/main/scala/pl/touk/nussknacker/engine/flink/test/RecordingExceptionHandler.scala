package pl.touk.nussknacker.engine.flink.test

import org.apache.flink.api.common.restartstrategy.RestartStrategies
import pl.touk.nussknacker.engine.api.exception.{EspExceptionInfo, NonTransientException}
import pl.touk.nussknacker.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionHandler}
import pl.touk.nussknacker.engine.flink.util.exception.{ConsumingNonTransientExceptions, RateMeterExceptionConsumer}
import pl.touk.nussknacker.test.WithDataList

class RecordingExceptionHandler extends FlinkEspExceptionHandler
  with ConsumingNonTransientExceptions
  with WithDataList[EspExceptionInfo[_ <: Throwable]] {

  override def restartStrategy: RestartStrategies.RestartStrategyConfiguration = RestartStrategies.noRestart()

  override protected val consumer: FlinkEspExceptionConsumer = new RateMeterExceptionConsumer(new FlinkEspExceptionConsumer {
    override def consume(exceptionInfo: EspExceptionInfo[NonTransientException]): Unit =
      add(exceptionInfo)
  })
}
