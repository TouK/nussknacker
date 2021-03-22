package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.streaming.api.windowing.triggers.{Trigger, TriggerResult}
import org.apache.flink.streaming.api.windowing.windows.Window

object triggers {

  //NOTE: composing triggers is tricky. We may want to e.g. ignore TriggerResult from delegate, but still we invoke e.g. onEventTime, because we want to handle triggers
  abstract class DelegatingTrigger[T, W <: Window](delegate: Trigger[_ >: T, W]) extends Trigger[T, W] {

    override def onElement(element: T, timestamp: Long, window: W, ctx: Trigger.TriggerContext): TriggerResult = delegate.onElement(element, timestamp, window, ctx)

    override def onProcessingTime(time: Long, window: W, ctx: Trigger.TriggerContext): TriggerResult = delegate.onProcessingTime(time, window, ctx)

    override def onEventTime(time: Long, window: W, ctx: Trigger.TriggerContext): TriggerResult = delegate.onEventTime(time, window, ctx)

    override def clear(window: W, ctx: Trigger.TriggerContext): Unit = delegate.clear(window, ctx)

    override def canMerge: Boolean = delegate.canMerge

    override def onMerge(window: W, ctx: Trigger.OnMergeContext): Unit = delegate.onMerge(window, ctx)


  }

  //Window won't be emitted on end, but after each event. This would be useful e.g. when we want to have
  //daily (i.e. for current day) aggregate for each incoming event, but we're not interested in daily summary on each midnight
  case class FireOnEachEvent[T, W <: Window](delegate: Trigger[_ >: T, W]) extends DelegatingTrigger[T, W](delegate) {
    override def onElement(element: T, timestamp: Long, window: W, ctx: Trigger.TriggerContext): TriggerResult = {
      val result = super.onElement(element, timestamp, window, ctx)
      result match {
        case TriggerResult.CONTINUE => TriggerResult.FIRE
        case TriggerResult.PURGE => TriggerResult.FIRE_AND_PURGE
        case fire => fire
      }
    }

    override def onProcessingTime(time: Long, window: W, ctx: Trigger.TriggerContext): TriggerResult = {
      super.onProcessingTime(time, window, ctx)
      TriggerResult.CONTINUE
    }

    override def onEventTime(time: Long, window: W, ctx: Trigger.TriggerContext): TriggerResult = {
      super.onEventTime(time, window, ctx)
      TriggerResult.CONTINUE
    }
  }

}
