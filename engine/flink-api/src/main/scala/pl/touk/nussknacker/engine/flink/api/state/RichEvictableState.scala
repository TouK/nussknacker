package pl.touk.nussknacker.engine.flink.api.state

import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.streaming.api.operators.ChainingStrategy
import org.apache.flink.streaming.api.watermark.Watermark
import pl.touk.nussknacker.engine.api.util.MultiMap
import pl.touk.nussknacker.engine.api.{InterpretationResult, ValueWithContext}

abstract class RichEvictableState[T] extends RichEvictableStateGeneric[T, ValueWithContext[Any]]

abstract class TimestampedEvictableState[T] extends TimestampedEvictableStateGeneric[T, ValueWithContext[Any]]

abstract class TimestampedEvictableStateGeneric[T, OUT] extends RichEvictableStateGeneric[MultiMap[Long, T], OUT] {

  override def processWatermark(mark: Watermark) = {
    super.processWatermark(mark)
    output.emitWatermark(mark)
  }

  def filterState(timeStamp: Long, lengthInMillis: Long): MultiMap[Long, T] = {
    stateValue.from(timeStamp - lengthInMillis)
  }

  def stateValue: MultiMap[Long, T] = {
    Option(state.value()).getOrElse(MultiMap[Long, T](Ordering.Long))
  }

}

abstract class RichEvictableStateGeneric[T, OUT] extends EvictableState[InterpretationResult, OUT] {

  setChainingStrategy(ChainingStrategy.ALWAYS)

  var state: ValueState[T] = _

  def stateDescriptor: ValueStateDescriptor[T]

  override def getState = state

  override def open() = {
    super.open()
    state = getRuntimeContext.getState(stateDescriptor)
  }

}