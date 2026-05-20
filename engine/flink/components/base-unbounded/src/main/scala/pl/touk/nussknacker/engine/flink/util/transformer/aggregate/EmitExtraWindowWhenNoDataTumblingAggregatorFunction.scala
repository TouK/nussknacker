package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.api.common.functions.{OpenContext, RuntimeContext}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.TimerService
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.{Context => NkContext, NodeId, ValueWithContext}
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, EngineRuntimeContext}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.util.keyed.KeyEnricher
import pl.touk.nussknacker.engine.util.KeyedValue

/**
 * It behaves similar to Flink's TumblingWindow with one difference that we produce extra zero aggregate for each key when no data arrived.
 * For this purpose we've used `AggregatorFunctionMixin` with `minimalResolutionMs = timeWindowLengthMillis`. We use it because
 * it handles out of order elements. The other difference from AggregatorFunction is that we emit event only in timer and handle
 * state eviction on ours own.
 */
class EmitExtraWindowWhenNoDataTumblingAggregatorFunction(
    protected val aggregator: Aggregator,
    protected val timeWindowLengthMillis: Long,
    protected val timeWindowOffsetMillis: Long,
    override val nodeId: NodeId,
    protected val aggregateElementType: TypingResult,
    protected override val aggregateTypeInformation: TypeInformation[AnyRef],
    val convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext
) extends KeyedProcessFunction[AnyRef, ValueWithContext[KeyedValue[AnyRef, AnyRef]], ValueWithContext[AnyRef]]
    with AggregatorFunctionMixin {

  type FlinkCtx =
    KeyedProcessFunction[AnyRef, ValueWithContext[KeyedValue[AnyRef, AnyRef]], ValueWithContext[AnyRef]]#Context
  type FlinkOnTimerCtx =
    KeyedProcessFunction[AnyRef, ValueWithContext[KeyedValue[AnyRef, AnyRef]], ValueWithContext[AnyRef]]#OnTimerContext

  @transient
  private var contextIdGenerator: ContextIdGenerator = _

  override def open(openContext: OpenContext): Unit = {
    super.open(openContext)
    initState()
    contextIdGenerator = convertToEngineRuntimeContext(getRuntimeContext).contextIdGenerator(nodeId.id)
  }

  override protected val minimalResolutionMs: Long = timeWindowLengthMillis

  override protected def initEvictionTimeState(): Unit = {
    // eviction is handled in onTimer — latestEvictionTimeForKey is not used
  }

  override def processElement(
      value: ValueWithContext[KeyedValue[AnyRef, AnyRef]],
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
    val currentKeys       = bucketsState.keys
    val finalVal          = computeFinalValue(previousTimestamp, currentKeys)
    out.collect(
      ValueWithContext(
        finalVal,
        KeyEnricher.enrichWithKey(NkContext(contextIdGenerator.nextContextId()), ctx.getCurrentKey)
      )
    )

    val rangeStart  = previousTimestamp - timeWindowLengthMillis + 1
    val hasMoreData = currentKeys.hasElementsFrom(rangeStart)

    if (hasMoreData) {
      ctx.timerService().registerEventTimeTimer(timestamp + timeWindowLengthMillis)
    } else {
      bucketsState.clear()
    }
  }

  override protected def doMoveEvictionTime(time: Long, timeService: TimerService): Unit = {
    // state eviction is controlled in onTimer
  }

}
