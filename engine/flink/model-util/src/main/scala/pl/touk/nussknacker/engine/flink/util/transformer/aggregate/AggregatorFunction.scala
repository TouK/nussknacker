package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.{ValueWithContext, Context => NkContext}
import pl.touk.nussknacker.engine.flink.api.state.LatelyEvictableStateFunction
import pl.touk.nussknacker.engine.flink.util.keyed.StringKeyedValue

import scala.collection.immutable.TreeMap

// This is the real SlidingWindow with slide = 1min - moving with time for each key. It reduce on each emit and store
// partial aggregations for each minute, instead of storing aggregates for each slide. This implementation doesn't emit event
// when some element left the slide. If you want to handle this situation, you should use `EmitWhenEventLeftAggregatorFunction`
// Flinks SlidingWindows are something that is often called HoppingWindow. They consume a lot of memory because each element
// is stored in multiple windows with different offsets.
class AggregatorFunction(protected val aggregator: Aggregator, protected val timeWindowLengthMillis: Long)
  extends LatelyEvictableStateFunction[ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef], TreeMap[Long, AnyRef]] {

  type FlinkCtx = KeyedProcessFunction[String, ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef]]#Context

  //TODO make it configurable
  private val minimalResolutionMs = 60000L

  override def processElement(value: ValueWithContext[StringKeyedValue[AnyRef]], ctx: FlinkCtx, out: Collector[ValueWithContext[AnyRef]]): Unit = {

    moveEvictionTime(timeWindowLengthMillis, ctx)

    val newElementInStateTimestamp = computeTimestampToStore(ctx)
    val newElement = value.value.value.asInstanceOf[aggregator.Element]
    val newState: TreeMap[Long, aggregator.Aggregate] = computeNewState(newElementInStateTimestamp, newElement, ctx)

    state.update(newState)
    handleElementAddedToState(newElementInStateTimestamp, newElement, value.context, ctx, out)

    val finalVal: AnyRef = computeFinalValue(newState)
    out.collect(ValueWithContext(finalVal, value.context))
  }

  protected def handleElementAddedToState(newElementInStateTimestamp: Long, newElement: aggregator.Element, nkCtx: NkContext,
                                          ctx: FlinkCtx, out: Collector[ValueWithContext[AnyRef]]): Unit = {
  }

  protected def computeFinalValue(newState: TreeMap[Long, aggregator.Aggregate]): AnyRef = {
    if (newState.isEmpty) {
      aggregator.getResult(aggregator.createAccumulator())
    } else {
      val foldedState = newState.values.reduce(aggregator.merge)
      aggregator.getResult(foldedState)
    }
  }

  private def computeNewState(newElementInStateTimestamp: Long, newValue: aggregator.Element, ctx: FlinkCtx): TreeMap[Long, aggregator.Aggregate] = {
    val current: TreeMap[Long, aggregator.Aggregate] = stateForTimestamp(currentState(), ctx.timestamp())

    val currentAggregate = current.getOrElse(newElementInStateTimestamp, aggregator.createAccumulator())
    val newAggregate = aggregator.add(newValue, currentAggregate).asInstanceOf[aggregator.Aggregate]

    current.updated(newElementInStateTimestamp, newAggregate)
  }

  private def computeTimestampToStore(ctx: FlinkCtx): Long = {
    (ctx.timestamp() / minimalResolutionMs) * minimalResolutionMs
  }

  protected def stateForTimestamp[T](stateValue: TreeMap[Long, T], timestamp: Long): TreeMap[Long, T] = {
    stateValue.from(timestamp - timeWindowLengthMillis + 1) // we must have exclusive range like Flink's sliding/tumbling have
  }

  protected def currentState(): TreeMap[Long, aggregator.Aggregate] =
    Option(state.value().asInstanceOf[TreeMap[Long, aggregator.Aggregate]]).getOrElse(TreeMap.empty(Ordering.Long))

  override protected def stateDescriptor: ValueStateDescriptor[TreeMap[Long, AnyRef]] =
    new ValueStateDescriptor[TreeMap[Long, AnyRef]]("state", classOf[TreeMap[Long, AnyRef]])
}
