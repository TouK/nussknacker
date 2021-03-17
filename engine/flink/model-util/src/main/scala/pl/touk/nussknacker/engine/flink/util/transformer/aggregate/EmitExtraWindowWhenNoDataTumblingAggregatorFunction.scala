package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.api.common.state.ValueState
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.TimerService
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.{ValueWithContext, Context => NkContext}
import pl.touk.nussknacker.engine.flink.api.state.StateHolder
import pl.touk.nussknacker.engine.flink.util.keyed.StringKeyedValue
import pl.touk.nussknacker.engine.flink.util.orderedmap.FlinkRangeMap
import pl.touk.nussknacker.engine.flink.util.orderedmap.FlinkRangeMap._

import scala.language.higherKinds

/**
 * It behaves similar to Flink's TumblingWindow with one difference that we produce extra zero aggregate for each key when no data arrived.
 * For this purpose we've used `AggregatorFunctionMixin` with `minimalResolutionMs = timeWindowLengthMillis`. We use it because
 * it handles out of order elements. The other difference from AggregatorFunction is that we emit event only in timer and handle
 * state eviction on ours own.
 */
class EmitExtraWindowWhenNoDataTumblingAggregatorFunction[MapT[K,V]](protected val aggregator: Aggregator, protected val timeWindowLengthMillis: Long,
                                                                     override val nodeId: NodeId, protected val aggregateElementType: TypingResult)
                                                                    (implicit override val rangeMap: FlinkRangeMap[MapT])
  extends KeyedProcessFunction[String, ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef]]
    with StateHolder[MapT[Long, AnyRef]]
    with AggregatorFunctionMixin[MapT] with AddedElementContextStateHolder[MapT] {

  type FlinkCtx = KeyedProcessFunction[String, ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef]]#Context
  type FlinkOnTimerCtx = KeyedProcessFunction[String, ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef]]#OnTimerContext

  @transient
  protected var state: ValueState[MapT[Long, AnyRef]] = _

  override def open(parameters: Configuration): Unit = {
    state = getRuntimeContext.getState(stateDescriptor)
    addedElementContext = getRuntimeContext.getState(addedElementContextDescriptor)
  }

  override protected val minimalResolutionMs: Long = timeWindowLengthMillis

  override def processElement(value: ValueWithContext[StringKeyedValue[AnyRef]], ctx: FlinkCtx, out: Collector[ValueWithContext[AnyRef]]): Unit = {
    addElementToState(value, ctx.timestamp(), ctx.timerService(), out)
  }

  override protected def handleElementAddedToState(newElementInStateTimestamp: Long, newElement: aggregator.Element, nkCtx: NkContext,
                                                   timerService: TimerService, out: Collector[ValueWithContext[AnyRef]]): Unit = {
    addedElementContext.update(readAddedElementContextOrInitial().updated(newElementInStateTimestamp, nkCtx))
    timerService.registerEventTimeTimer(newElementInStateTimestamp + timeWindowLengthMillis)
  }

  override def onTimer(timestamp: Long, ctx: FlinkOnTimerCtx, out: Collector[ValueWithContext[AnyRef]]): Unit = {
    val currentStateValue = readStateOrInitial()
    val previousTimestamp = timestamp - timeWindowLengthMillis

    readAddedElementContextOrInitial().toRO(previousTimestamp).toScalaMapRO.lastOption.foreach {
      case (_, nkCtx) =>
        val finalVal = computeFinalValue(currentStateValue, previousTimestamp)
        out.collect(ValueWithContext(finalVal, nkCtx))
    }
    
    val previousTimestampStateAndRest = stateForTimestampToReadUntilEnd(currentStateValue, previousTimestamp)
    if (previousTimestampStateAndRest.toScalaMapRO.isEmpty) {
      evictStates()
    } else {
      ctx.timerService().registerEventTimeTimer(timestamp + timeWindowLengthMillis)
    }
  }

  override protected def updateState(stateValue: MapT[Long, AnyRef], stateValidity: Long, timeService: TimerService): Unit = {
    state.update(stateValue)
    invalidateAddedElementContextState(stateValue)
  }

  protected def evictStates(): Unit = {
    state.clear()
    addedElementContext.clear()
  }

  override protected def readState(): MapT[Long, AnyRef] = state.value()

}
