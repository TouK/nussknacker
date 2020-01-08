package pl.touk.nussknacker.engine.process.helpers

import java.util.concurrent.CopyOnWriteArrayList

import org.apache.flink.api.common.restartstrategy.RestartStrategies
import pl.touk.nussknacker.engine.api.exception.{EspExceptionInfo, NonTransientException}
import pl.touk.nussknacker.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionHandler}
import pl.touk.nussknacker.engine.flink.util.exception.{ConsumingNonTransientExceptions, RateMeterExceptionConsumer}

object CommonTestHelpers {

  class RecordingExceptionHandler extends FlinkEspExceptionHandler
    with ConsumingNonTransientExceptions
    with WithDataList[EspExceptionInfo[_ <: Throwable]] {

    override def restartStrategy: RestartStrategies.RestartStrategyConfiguration = RestartStrategies.noRestart()

    override protected val consumer: FlinkEspExceptionConsumer = new RateMeterExceptionConsumer(new FlinkEspExceptionConsumer {
      override def consume(exceptionInfo: EspExceptionInfo[NonTransientException]): Unit =
        add(exceptionInfo)
    })
  }
}

trait WithDataList[T] extends Serializable {

  private val dataList = new CopyOnWriteArrayList[T]

  def add(element: T) : Unit = dataList.add(element)

  def data : List[T] = {
    dataList.toArray.toList.map(_.asInstanceOf[T])
  }

  def clear() : Unit = {
    dataList.clear()
  }
}