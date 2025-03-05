package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.api.common.functions.{OpenContext, RuntimeContext}
import org.apache.flink.api.common.state.ValueState
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.TimerService
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.{Context => NkContext, NodeId, ValueWithContext}
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, EngineRuntimeContext}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.api.state.StateHolder
import pl.touk.nussknacker.engine.flink.util.keyed.{KeyEnricher, StringKeyedValue}
import pl.touk.nussknacker.engine.flink.util.orderedmap.FlinkRangeMap
import pl.touk.nussknacker.engine.flink.util.orderedmap.FlinkRangeMap._

import scala.language.higherKinds

/**
 * It behaves similar to Flink's TumblingWindow with one difference that we produce extra zero aggregate for each key when no data arrived.
 * For this purpose we've used `AggregatorFunctionMixin` with `minimalResolutionMs = timeWindowLengthMillis`. We use it because
 * it handles out of order elements. The other difference from AggregatorFunction is that we emit event only in timer and handle
 * state eviction on ours own.
 */
class EmitExtraWindowWhenNoDataTumblingAggregatorFunction[MapT[K, V]](
    protected val aggregator: Aggregator,
    protected val timeWindowLengthMillis: Long,
    protected val timeWindowOffsetMillis: Long,
    override val nodeId: NodeId,
    protected val aggregateElementType: TypingResult,
    protected override val aggregateTypeInformation: TypeInformation[AnyRef],
    val convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext
)(implicit override val rangeMap: FlinkRangeMap[MapT])
    extends KeyedProcessFunction[String, ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef]]
    with StateHolder[MapT[Long, AnyRef]]
    with AggregatorFunctionMixin[MapT] {

  type FlinkCtx =
    KeyedProcessFunction[String, ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef]]#Context
  type FlinkOnTimerCtx =
    KeyedProcessFunction[String, ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef]]#OnTimerContext

  @transient
  protected var state: ValueState[MapT[Long, AnyRef]] = _

  @transient
  private var contextIdGenerator: ContextIdGenerator = _

  override def open(openContext: OpenContext): Unit = {
    state = getRuntimeContext.getState(stateDescriptor)
    contextIdGenerator = convertToEngineRuntimeContext(getRuntimeContext).contextIdGenerator(nodeId.id)
  }

  override protected val minimalResolutionMs: Long = timeWindowLengthMillis

  override def processElement(
      value: ValueWithContext[StringKeyedValue[AnyRef]],
      ctx: FlinkCtx,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    addElementToState(value, ctx.timestamp() - timeWindowOffsetMillis, ctx.timerService(), out)
  }

  override protected def handleElementAddedToState(
      newElementInStateTimestamp: Long,
      newElement: aggregator.Element,
      nkCtx: NkContext,
      timerService: TimerService,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    timerService.registerEventTimeTimer(newElementInStateTimestamp + timeWindowLengthMillis)
  }

  override def onTimer(timestamp: Long, ctx: FlinkOnTimerCtx, out: Collector[ValueWithContext[AnyRef]]): Unit = {
    val previousTimestamp = timestamp - timeWindowLengthMillis
    val currentStateValue = readStateOrInitial()
    val finalVal          = computeFinalValue(currentStateValue, previousTimestamp)
    out.collect(
      ValueWithContext(
        finalVal,
        KeyEnricher.enrichWithKey(NkContext(contextIdGenerator.nextContextId()), ctx.getCurrentKey)
      )
    )

    val previousTimestampStateAndRest = stateForTimestampToReadUntilEnd(currentStateValue, previousTimestamp)
    if (previousTimestampStateAndRest.toScalaMapRO.isEmpty) {
      evictStates()
    } else {
      ctx.timerService().registerEventTimeTimer(timestamp + timeWindowLengthMillis)
    }
  }

  override protected def updateState(
      stateValue: MapT[Long, AnyRef],
      stateValidity: Long,
      timeService: TimerService
  ): Unit = {
    state.update(stateValue)
  }

  override protected def doMoveEvictionTime(time: Long, timeService: TimerService): Unit = {
    // state eviction is controlled in onTimer
  }

  protected def evictStates(): Unit = {
    state.clear()
  }

  override protected def readState(): MapT[Long, AnyRef] = state.value()

}
