package pl.touk.nussknacker.engine.flink.api.state

import org.apache.flink.api.common.functions.{OpenContext, RichFunction}
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.streaming.api.TimerService
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.functions.co.CoProcessFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.flink.api.{TimeMode, WithTimeMode}

abstract class EvictableStateFunction[In, Out, StateType] extends KeyedProcessFunction[String, In, Out] {

  @transient
  protected var lastEventTimeForKey: ValueState[java.lang.Long] = _

  @transient
  protected var state: ValueState[StateType] = _

  protected def stateDescriptor: ValueStateDescriptor[StateType]

  override def open(openContext: OpenContext): Unit = {
    super.open(openContext)
    lastEventTimeForKey = getRuntimeContext.getState[java.lang.Long](
      new ValueStateDescriptor[java.lang.Long]("timers", classOf[java.lang.Long])
    )
    state = getRuntimeContext.getState(stateDescriptor)
  }

  override def onTimer(
      timestamp: Long,
      ctx: KeyedProcessFunction[String, In, Out]#OnTimerContext,
      out: Collector[Out]
  ): Unit = {
    val noNewerEventsArrived = lastEventTimeForKey.value() == timestamp
    if (noNewerEventsArrived) {
      state.clear()
      lastEventTimeForKey.update(null)
    }
  }

  protected def moveEvictionTime(offset: Long, ctx: KeyedProcessFunction[String, In, Out]#Context): Unit = {
    val time = ctx.timestamp() + offset
    // we don't delete former timer, because it's inefficient
    ctx.timerService().registerEventTimeTimer(time)
    lastEventTimeForKey.update(time)
  }

}

abstract class LatelyEvictableStateFunction[In, Out, StateType, Key]
    extends KeyedProcessFunction[Key, In, Out]
    with LatelyEvictableStateFunctionMixin[In, Out, StateType, Key] {

  // Defaults to event time for backward compatibility
  override def timeMode: TimeMode = TimeMode.EventTime

  override def onTimer(
      timestamp: Long,
      ctx: KeyedProcessFunction[Key, In, Out]#OnTimerContext,
      out: Collector[Out]
  ): Unit = {
    handleOnTimer(timestamp, ctx.timerService)
  }

  protected def moveEvictionTime(offset: Long, ctx: KeyedProcessFunction[Key, In, Out]#Context): Unit = {
    doMoveEvictionTime(currentTime(ctx) + offset, ctx.timerService())
  }

}

trait LatelyEvictableStateFunctionMixin[In, Out, StateType, Key]
    extends RichFunction
    with StateHolder[StateType]
    with WithTimeMode[Key, In, Out] {
  self: KeyedProcessFunction[Key, In, Out] =>

  @transient
  protected var latestEvictionTimeForKey: ValueState[java.lang.Long] = _

  @transient
  protected var state: ValueState[StateType] = _

  override def open(openContext: OpenContext): Unit = {
    latestEvictionTimeForKey = getRuntimeContext.getState[java.lang.Long](
      new ValueStateDescriptor[java.lang.Long]("timers", classOf[java.lang.Long])
    )
    state = getRuntimeContext.getState(stateDescriptor)
  }

  override protected def readState(): StateType = state.value()

  override protected def updateState(stateValue: StateType, stateValidity: Long, timeService: TimerService): Unit = {
    state.update(stateValue)
    doMoveEvictionTime(stateValidity, timeService)
  }

  protected def handleOnTimer(timestamp: Long, timerService: TimerService): Unit = {
    val latestEvictionTimeValue = latestEvictionTimeForKey.value()
    val noLaterEventsArrived    = latestEvictionTimeValue == timestamp
    if (noLaterEventsArrived) {
      evictStates()
    } else if (latestEvictionTimeValue != null) {
      registerTimer(timerService, latestEvictionTimeValue)
    }
  }

  protected def evictStates(): Unit = {
    state.clear()
    latestEvictionTimeForKey.update(null)
  }

  override protected def doMoveEvictionTime(time: Long, timeService: TimerService): Unit = {
    val latestEvictionTimeValue = latestEvictionTimeForKey.value()
    val maxEvictionTime = if (latestEvictionTimeValue == null || time > latestEvictionTimeValue) {
      time
    } else {
      latestEvictionTimeValue.longValue()
    }
    if (latestEvictionTimeValue == null) {
      registerTimer(timeService, maxEvictionTime)
    }
    latestEvictionTimeForKey.update(maxEvictionTime)
  }

}

trait StateHolder[StateType] {

  protected def stateDescriptor: ValueStateDescriptor[StateType]

  protected def readState(): StateType

  protected def updateState(stateValue: StateType, stateValidity: Long, timeService: TimerService): Unit

  protected def doMoveEvictionTime(time: Long, timeService: TimerService): Unit

}
