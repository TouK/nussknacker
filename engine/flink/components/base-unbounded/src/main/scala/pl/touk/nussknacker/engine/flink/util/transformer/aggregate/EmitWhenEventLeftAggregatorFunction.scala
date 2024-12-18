package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.api.common.functions.{OpenContext, RuntimeContext}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.TimerService
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, EngineRuntimeContext}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.{Context => NkContext, NodeId, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.state.LatelyEvictableStateFunction
import pl.touk.nussknacker.engine.flink.util.keyed.{KeyEnricher, StringKeyedValue}
import pl.touk.nussknacker.engine.flink.util.orderedmap.FlinkRangeMap
import pl.touk.nussknacker.engine.flink.util.orderedmap.FlinkRangeMap._

import scala.language.higherKinds

/**
 * It behaves the same as AggregatorFunction with one difference that also publish events when some event will left the slide.
 */
class EmitWhenEventLeftAggregatorFunction[MapT[K, V]](
    protected val aggregator: Aggregator,
    protected val timeWindowLengthMillis: Long,
    override val nodeId: NodeId,
    protected val aggregateElementType: TypingResult,
    override protected val aggregateTypeInformation: TypeInformation[AnyRef],
    val convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext
)(implicit override val rangeMap: FlinkRangeMap[MapT])
    extends LatelyEvictableStateFunction[
      ValueWithContext[StringKeyedValue[AnyRef]],
      ValueWithContext[AnyRef],
      MapT[Long, AnyRef]
    ]
    with AggregatorFunctionMixin[MapT] {

  @transient
  private var contextIdGenerator: ContextIdGenerator = _

  type FlinkCtx =
    KeyedProcessFunction[String, ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef]]#Context
  type FlinkOnTimerCtx =
    KeyedProcessFunction[String, ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef]]#OnTimerContext

  override def open(openContext: OpenContext): Unit = {
    super.open(openContext)
    contextIdGenerator = convertToEngineRuntimeContext(getRuntimeContext).contextIdGenerator(nodeId.id)
  }

  override def processElement(
      value: ValueWithContext[StringKeyedValue[AnyRef]],
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
    val currentStateValue = readStateOrInitial()
    handleElementLeftSlide(currentStateValue, timestamp, ctx, out)
    super.onTimer(timestamp, ctx, out)
  }

  protected def handleElementLeftSlide(
      currentStateValue: MapT[Long, aggregator.Aggregate],
      timestamp: Long,
      ctx: FlinkOnTimerCtx,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    val stateForRecentlySentEvent = currentStateValue.toScalaMapRO.lastOption
      .map { case (lastTimestamp, _) =>
        stateForTimestampToReadUntilEnd(
          currentStateValue,
          lastTimestamp
        ) // shouldn't we save somewhere recently sent timestamp?
      }
      .getOrElse(currentStateValue)
    if (stateForRecentlySentEvent.toRO(timestamp - timeWindowLengthMillis).toScalaMapRO.nonEmpty) {
      val finalVal = computeFinalValue(currentStateValue, timestamp)
      out.collect(
        ValueWithContext(
          finalVal,
          KeyEnricher.enrichWithKey(NkContext(contextIdGenerator.nextContextId()), ctx.getCurrentKey)
        )
      )
    }
  }

}
