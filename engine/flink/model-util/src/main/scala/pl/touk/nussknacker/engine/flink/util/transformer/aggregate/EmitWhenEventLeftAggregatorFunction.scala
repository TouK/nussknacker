package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.TimerService
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.{ValueWithContext, Context => NkContext}
import pl.touk.nussknacker.engine.flink.api.state.LatelyEvictableStateFunction
import pl.touk.nussknacker.engine.flink.util.keyed.StringKeyedValue

import scala.collection.immutable.TreeMap

/**
 * It behaves the same as AggregatorFunction with one difference that also publish events when some event will left the slide.
 */
class EmitWhenEventLeftAggregatorFunction(protected val aggregator: Aggregator, protected val timeWindowLengthMillis: Long, override val nodeId: NodeId)
  extends LatelyEvictableStateFunction[ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef], TreeMap[Long, AnyRef]]
    with AggregatorFunctionMixin with AddedElementContextStateHolder {

  type FlinkCtx = KeyedProcessFunction[String, ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef]]#Context
  type FlinkOnTimerCtx = KeyedProcessFunction[String, ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef]]#OnTimerContext

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    addedElementContext = getRuntimeContext.getState(addedElementContextDescriptor)
  }

  override def processElement(value: ValueWithContext[StringKeyedValue[AnyRef]], ctx: FlinkCtx, out: Collector[ValueWithContext[AnyRef]]): Unit = {
    handleNewElementAdded(value, ctx.timestamp(), ctx.timerService(), out)
  }

  override protected def handleElementAddedToState(newElementInStateTimestamp: Long, newElement: aggregator.Element, nkCtx: NkContext,
                                                   timerService: TimerService, out: Collector[ValueWithContext[AnyRef]]): Unit = {
    addedElementContext.update(readAddedElementContextOrInitial().updated(newElementInStateTimestamp, nkCtx))
    timerService.registerEventTimeTimer(newElementInStateTimestamp + timeWindowLengthMillis)
  }

  override def onTimer(timestamp: Long, ctx: FlinkOnTimerCtx, out: Collector[ValueWithContext[AnyRef]]): Unit = {
    val currentStateValue = readStateOrInitial()
    handleElementLeftSlide(currentStateValue, timestamp, ctx.timerService(), out)
    super.onTimer(timestamp, ctx, out)
  }

  protected def handleElementLeftSlide(currentStateValue: TreeMap[Long, aggregator.Aggregate], timestamp: Long,
                                       timerService: TimerService, out: Collector[ValueWithContext[AnyRef]]): Unit = {
    val stateForRecentlySentEvent = currentStateValue.lastOption.map {
      case (lastTimestamp, _) => stateForTimestampToReadUntilEnd(currentStateValue, lastTimestamp)  // shouldn't we save somewhere recently sent timestamp?
    }.getOrElse(currentStateValue)
    for {
      lastEntryToRemove <- stateForRecentlySentEvent.to(timestamp - timeWindowLengthMillis).lastOption
      (lastTimestampToRemove, _) = lastEntryToRemove
      matchingContext <- readAddedElementContextOrInitial().get(lastTimestampToRemove)
    } {
      val finalVal = computeFinalValue(currentStateValue, timestamp)
      out.collect(ValueWithContext(finalVal, matchingContext))
    }
  }

  override protected def updateState(stateValue: TreeMap[Long, AnyRef], stateValidity: Long, timeService: TimerService): Unit = {
    super.updateState(stateValue, stateValidity, timeService)
    invalidateAddedElementContextState(stateValue)
  }

  override protected def evictStates(): Unit = {
    super.evictStates()
    addedElementContext.clear()
  }

}