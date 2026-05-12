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

import scala.jdk.CollectionConverters._

class EmitWhenEventLeftAggregatorFunctionWithMapState(
    protected val aggregator: Aggregator,
    protected val timeWindowLengthMillis: Long,
    override val nodeId: NodeId,
    protected val aggregateElementType: TypingResult,
    protected val aggregateTypeInformation: TypeInformation[AnyRef],
    val convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext
) extends KeyedProcessFunction[AnyRef, ValueWithContext[KeyedValue[AnyRef, AnyRef]], ValueWithContext[AnyRef]]
    with AggregatorFunctionWithMapStateMixin {

  type FlinkCtx =
    KeyedProcessFunction[AnyRef, ValueWithContext[KeyedValue[AnyRef, AnyRef]], ValueWithContext[AnyRef]]#Context
  type FlinkOnTimerCtx =
    KeyedProcessFunction[AnyRef, ValueWithContext[KeyedValue[AnyRef, AnyRef]], ValueWithContext[AnyRef]]#OnTimerContext

  @transient
  private var contextIdGenerator: ContextIdGenerator = _

  override def open(openContext: OpenContext): Unit = {
    super.open(openContext)
    initMapState()
    contextIdGenerator = convertToEngineRuntimeContext(getRuntimeContext).contextIdGenerator(nodeId.id)
  }

  override def processElement(
      value: ValueWithContext[KeyedValue[AnyRef, AnyRef]],
      ctx: FlinkCtx,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    handleNewElementAdded(value, ctx.timestamp(), ctx.timerService(), out)
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
    handleElementLeftSlide(timestamp, ctx, out)
    handleOnTimer(timestamp, ctx.timerService())
  }

  private def handleElementLeftSlide(
      timestamp: Long,
      ctx: FlinkOnTimerCtx,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    // Check if entries exist in [maxBucketTs - windowLength + 1, timestamp - windowLength].
    // This range represents entries that were visible when the last event was emitted
    // but are now outside the window boundary.
    val maxBucketTsOpt = bucketState.keys().asScala.map(_.longValue()).maxOption

    maxBucketTsOpt.foreach { maxBucketTs =>
      val rangeStart = maxBucketTs - timeWindowLengthMillis + 1
      val rangeEnd   = timestamp - timeWindowLengthMillis

      if (rangeEnd >= rangeStart) {
        val hasLeavingEntries = bucketState
          .keys()
          .asScala
          .exists(key => key >= rangeStart && key <= rangeEnd)

        if (hasLeavingEntries) {
          val finalVal = computeFinalValue(timestamp)
          out.collect(
            ValueWithContext(
              finalVal,
              KeyEnricher.enrichWithKey(NkContext(contextIdGenerator.nextContextId()), ctx.getCurrentKey)
            )
          )
        }
      }
    }
  }

}
