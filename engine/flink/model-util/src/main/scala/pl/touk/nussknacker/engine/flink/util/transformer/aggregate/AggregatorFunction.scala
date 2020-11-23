package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.streaming.api.TimerService
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.{ValueWithContext, Context => NkContext}
import pl.touk.nussknacker.engine.flink.api.state.{LatelyEvictableStateFunction, StateHolder}
import pl.touk.nussknacker.engine.flink.util.keyed.StringKeyedValue

import scala.collection.immutable.TreeMap

// This is the real SlidingWindow with slide = 1min - moving with time for each key. It reduce on each emit and store
// partial aggregations for each minute, instead of storing aggregates for each slide. This implementation doesn't emit event
// when some element left the slide. If you want to handle this situation, you should use `EmitWhenEventLeftAggregatorFunction`
// Flinks SlidingWindows are something that is often called HoppingWindow. They consume a lot of memory because each element
// is stored in multiple windows with different offsets.
class AggregatorFunction(protected val aggregator: Aggregator, protected val timeWindowLengthMillis: Long)
  extends LatelyEvictableStateFunction[ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef], TreeMap[Long, AnyRef]]
  with AggregatorFunctionMixin {

  type FlinkCtx = KeyedProcessFunction[String, ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef]]#Context

  override def processElement(value: ValueWithContext[StringKeyedValue[AnyRef]], ctx: FlinkCtx, out: Collector[ValueWithContext[AnyRef]]): Unit = {
    handleNewElementAdded(value, ctx.timestamp(), ctx.timerService(), out)
  }

}

trait AggregatorFunctionMixin { self: StateHolder[TreeMap[Long, AnyRef]] =>

  //TODO make it configurable
  protected val minimalResolutionMs = 60000L

  protected def allowedOutOfOrderMs: Long = timeWindowLengthMillis

  protected val aggregator: Aggregator

  protected def timeWindowLengthMillis: Long


  protected def handleNewElementAdded(value: ValueWithContext[StringKeyedValue[AnyRef]],
                                      timestamp: Long, timeService: TimerService,
                                      out: Collector[ValueWithContext[AnyRef]]): Unit = {
    val newState = addElementToState(value, timestamp, timeService, out)
    val finalVal = computeFinalValue(newState, timestamp)
    out.collect(ValueWithContext(finalVal, value.context))
  }

  protected def addElementToState(value: ValueWithContext[StringKeyedValue[AnyRef]],
                                  timestamp: Long, timeService: TimerService,
                                  out: Collector[ValueWithContext[AnyRef]]): TreeMap[Long, aggregator.Aggregate] = {
    val newElementInStateTimestamp = computeTimestampToStore(timestamp)
    val newElement = value.value.value.asInstanceOf[aggregator.Element]
    val newState = computeNewState(newElementInStateTimestamp, newElement)

    updateState(newState, newElementInStateTimestamp + timeWindowLengthMillis, timeService)
    handleElementAddedToState(newElementInStateTimestamp, newElement, value.context, timeService, out)
    newState
  }

  // for extending classes purpose
  protected def handleElementAddedToState(newElementInStateTimestamp: Long, newElement: aggregator.Element, nkCtx: NkContext,
                                          timeService: TimerService, out: Collector[ValueWithContext[AnyRef]]): Unit = {
  }

  protected def computeFinalValue(newState: TreeMap[Long, aggregator.Aggregate], timestamp: Long): AnyRef = {
    val newStateTruncatedToTimestamp = stateForTimestampToRead(newState, timestamp)
    if (newStateTruncatedToTimestamp.isEmpty) {
      aggregator.getResult(aggregator.createAccumulator())
    } else {
      val foldedState = newStateTruncatedToTimestamp.values.reduce(aggregator.merge)
      aggregator.getResult(foldedState)
    }
  }

  private def computeNewState(newElementInStateTimestamp: Long, newValue: aggregator.Element): TreeMap[Long, aggregator.Aggregate] = {
    val current: TreeMap[Long, aggregator.Aggregate] = stateForTimestampToSave(readStateOrInitial(), newElementInStateTimestamp)

    // We do not create aggregate and add to it neutral element to avoid unnecessary buckets in our state
    if (aggregator.isNeutralForAccumulator(newValue)) {
      current
    } else {
      val currentAggregate = current.getOrElse(newElementInStateTimestamp, aggregator.createAccumulator())
      val newAggregate = aggregator.add(newValue, currentAggregate).asInstanceOf[aggregator.Aggregate]

      current.updated(newElementInStateTimestamp, newAggregate)
    }
  }

  private def computeTimestampToStore(timestamp: Long): Long = {
    (timestamp / minimalResolutionMs) * minimalResolutionMs
  }

  protected def stateForTimestampToSave[T](stateValue: TreeMap[Long, T], timestamp: Long): TreeMap[Long, T] = {
    stateValue.from(timestamp - timeWindowLengthMillis + 1 - allowedOutOfOrderMs) // we must have exclusive range like Flink's sliding/tumbling have
  }

  protected def stateForTimestampToRead[T](stateValue: TreeMap[Long, T], timestamp: Long): TreeMap[Long, T] = {
    stateForTimestampToReadUntilEnd(stateValue, timestamp).to(timestamp) // we must have exclusive range like Flink's sliding/tumbling have
  }

  protected def stateForTimestampToReadUntilEnd[T](stateValue: TreeMap[Long, T], timestamp: Long): TreeMap[Long, T] = {
    stateValue.from(timestamp - timeWindowLengthMillis + 1)
  }

  protected def readStateOrInitial(): TreeMap[Long, aggregator.Aggregate] =
    Option(readState().asInstanceOf[TreeMap[Long, aggregator.Aggregate]]).getOrElse(TreeMap.empty(Ordering.Long))

  override protected def stateDescriptor: ValueStateDescriptor[TreeMap[Long, AnyRef]] =
    new ValueStateDescriptor[TreeMap[Long, AnyRef]]("state", classOf[TreeMap[Long, AnyRef]])

}
