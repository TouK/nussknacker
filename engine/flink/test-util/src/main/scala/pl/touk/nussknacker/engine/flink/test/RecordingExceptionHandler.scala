package pl.touk.nussknacker.engine.flink.test

import org.apache.flink.api.common.restartstrategy.RestartStrategies
import pl.touk.nussknacker.engine.api.exception.{EspExceptionInfo, NonTransientException}
import pl.touk.nussknacker.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionHandler}
import pl.touk.nussknacker.engine.flink.util.exception.{ConsumingNonTransientExceptions, RateMeterExceptionConsumer}
import pl.touk.nussknacker.test.WithDataList

import java.util.UUID

object RecordingExceptionHandler extends WithDataList[(String, EspExceptionInfo[_ <: Throwable])] {
  def dataFor(id: String): List[EspExceptionInfo[_ <: Throwable]] = RecordingExceptionHandler.data.collect {
    case (eid, ex) if eid == id => ex
  }

}

class RecordingExceptionHandler(id: String = UUID.randomUUID().toString) extends FlinkEspExceptionHandler
  with ConsumingNonTransientExceptions {

  override protected val consumer: FlinkEspExceptionConsumer = new RateMeterExceptionConsumer(new FlinkEspExceptionConsumer {
    override def consume(exceptionInfo: EspExceptionInfo[NonTransientException]): Unit =
      RecordingExceptionHandler.add((id, exceptionInfo))
  })

  def data: List[EspExceptionInfo[_ <: Throwable]] = RecordingExceptionHandler.dataFor(id)

  def clear(): Unit = RecordingExceptionHandler.clear(_._1 == id)

}
