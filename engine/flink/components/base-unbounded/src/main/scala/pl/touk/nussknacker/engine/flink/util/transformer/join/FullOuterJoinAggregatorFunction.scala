package pl.touk.nussknacker.engine.flink.util.transformer.join

import org.apache.flink.api.common.functions.{OpenContext, RuntimeContext}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.util.keyed.StringKeyedValue
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.{Aggregator, AggregatorFunctionMixin}
import pl.touk.nussknacker.engine.util.KeyedValue

class FullOuterJoinAggregatorFunction(
    protected val aggregator: Aggregator,
    protected val timeWindowLengthMillis: Long,
    override val nodeId: NodeId,
    override val nodeName: NodeName,
    protected val aggregateElementType: TypingResult,
    override protected val aggregateTypeInformation: TypeInformation[AnyRef],
    val convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext,
    val keyFieldName: String
) extends KeyedProcessFunction[String, ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef]]
    with AggregatorFunctionMixin {

  type FlinkCtx =
    KeyedProcessFunction[String, ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef]]#Context

  override def open(openContext: OpenContext): Unit = {
    super.open(openContext)
    initState()
  }

  override def processElement(
      in: ValueWithContext[StringKeyedValue[AnyRef]],
      ctx: FlinkCtx,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    addElementToState(in, ctx.timestamp(), ctx.timerService(), out)
    val res = computeFinalValue(ctx.timestamp(), bucketsState.keys).asInstanceOf[java.util.Map[String, AnyRef]]
    res.put(keyFieldName, in.value.key)
    out.collect(ValueWithContext(res, in.context.clearUserVariables))
  }

  override def onTimer(
      timestamp: Long,
      ctx: KeyedProcessFunction[
        String,
        ValueWithContext[StringKeyedValue[AnyRef]],
        ValueWithContext[AnyRef]
      ]#OnTimerContext,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    handleOnTimer(timestamp, ctx.timerService())
  }

}
