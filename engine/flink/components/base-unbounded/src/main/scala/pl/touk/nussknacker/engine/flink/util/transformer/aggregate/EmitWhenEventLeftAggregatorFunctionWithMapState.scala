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
    initBucketsState()
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
    val allKeys = bucketsState.keys.asScala.map(_.longValue()).toList

    allKeys.maxOption.foreach { maxBucketTs =>
      val leavingRangeStart = maxBucketTs - timeWindowLengthMillis + 1
      val leavingRangeEnd   = timestamp - timeWindowLengthMillis

      val hasLeavingEntries =
        leavingRangeEnd >= leavingRangeStart &&
          allKeys.exists(k => k >= leavingRangeStart && k <= leavingRangeEnd)

      if (hasLeavingEntries) {
        val foldRangeStart = timestamp - timeWindowLengthMillis + 1
        var count          = 0

        val foldedState = allKeys
          .filter(k => k >= foldRangeStart && k <= timestamp)
          .sorted
          .foldLeft(aggregator.createAccumulator()) { (acc, key) =>
            count += 1
            aggregator.merge(acc, bucketsState.get(key))
          }

        retrievedBucketsHistogram.update(count)
        val finalVal = aggregator.alignToExpectedType(aggregator.getResult(foldedState), outputType)

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
