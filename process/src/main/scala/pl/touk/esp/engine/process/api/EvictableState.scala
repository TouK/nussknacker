package pl.touk.esp.engine.process.api

import org.apache.flink.api.common.state.{State, ValueState, ValueStateDescriptor}
import org.apache.flink.api.common.typeutils.base.StringSerializer
import org.apache.flink.streaming.api.operators._

/*
  Constraints
  - only EventTime
  - keys are strings
 */
abstract class EvictableState[In, Out] extends AbstractStreamOperator[Out]
    with OneInputStreamOperator[In, Out] with Triggerable[String, String] {

  var internalTimerService : InternalTimerService[String] = _

  var timerState : ValueState[java.lang.Long] = _

  override def open() = {
    super.open()
    timerState = getRuntimeContext.getState[java.lang.Long](new ValueStateDescriptor[java.lang.Long]("timers", classOf[java.lang.Long]))
    internalTimerService = getInternalTimerService("evictable-timers", new StringSerializer, this)
  }

  def getState: State

  override def onProcessingTime(timer: InternalTimer[String, String]) = {
    //FIXME: a moze blad??
  }

  override def onEventTime(timer: InternalTimer[String, String]) = {
    setCurrentKey(timer.getKey)
    getState.clear()
    timerState.update(null)
  }


  protected final def setEvictionTimeForCurrentKey(time: Long) = {
    val key = getCurrentKey.toString
    Option(timerState.value()).foreach(time => internalTimerService.deleteEventTimeTimer(key, time))
    internalTimerService.registerEventTimeTimer(key, time)
    timerState.update(time)
  }

}

