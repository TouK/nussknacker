package pl.touk.nussknacker.engine.flink.util.transformer.join

import org.apache.flink.api.common.functions.{OpenContext, RuntimeContext}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.co.CoProcessFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.util.keyed.StringKeyedValue
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.{Aggregator, AggregatorFunctionMixin}
import pl.touk.nussknacker.engine.util.KeyedValue

class SingleSideJoinAggregatorFunction(
    protected val aggregator: Aggregator,
    protected val timeWindowLengthMillis: Long,
    override val nodeId: NodeId,
    override val nodeName: NodeName,
    protected val aggregateElementType: TypingResult,
    override protected val aggregateTypeInformation: TypeInformation[AnyRef],
    val convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext
) extends CoProcessFunction[ValueWithContext[String], ValueWithContext[
      StringKeyedValue[AnyRef]
    ], ValueWithContext[AnyRef]]
    with AggregatorFunctionMixin {

  type FlinkCtx = CoProcessFunction[ValueWithContext[String], ValueWithContext[
    StringKeyedValue[AnyRef]
  ], ValueWithContext[AnyRef]]#Context

  override def open(openContext: OpenContext): Unit = {
    super.open(openContext)
    initState()
  }

  override def processElement1(
      in1: ValueWithContext[String],
      ctx: FlinkCtx,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    val finalVal = computeFinalValue(ctx.timestamp(), bucketsState.keys)
    out.collect(ValueWithContext(finalVal, in1.context))
  }

  override def processElement2(
      in2: ValueWithContext[StringKeyedValue[AnyRef]],
      ctx: FlinkCtx,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    addElementToState(
      in2.asInstanceOf[ValueWithContext[KeyedValue[AnyRef, AnyRef]]],
      ctx.timestamp(),
      ctx.timerService(),
      out
    )
  }

  override def onTimer(
      timestamp: Long,
      ctx: CoProcessFunction[ValueWithContext[String], ValueWithContext[
        StringKeyedValue[AnyRef]
      ], ValueWithContext[AnyRef]]#OnTimerContext,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    handleOnTimer(timestamp, ctx.timerService)
  }

}
