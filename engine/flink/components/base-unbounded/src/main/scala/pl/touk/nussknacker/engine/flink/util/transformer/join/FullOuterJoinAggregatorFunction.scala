package pl.touk.nussknacker.engine.flink.util.transformer.join

import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.api.state.LatelyEvictableStateFunction
import pl.touk.nussknacker.engine.flink.util.keyed.StringKeyedValue
import pl.touk.nussknacker.engine.flink.util.orderedmap.FlinkRangeMap
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.{Aggregator, AggregatorFunctionMixin}

import scala.language.higherKinds

class FullOuterJoinAggregatorFunction[MapT[_, _]](
    protected val aggregator: Aggregator,
    protected val timeWindowLengthMillis: Long,
    override val nodeId: NodeId,
    protected val aggregateElementType: TypingResult,
    override protected val aggregateTypeInformation: TypeInformation[AnyRef],
    val convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext,
    val keyFieldName: String
)(implicit override val rangeMap: FlinkRangeMap[MapT])
    extends LatelyEvictableStateFunction[
      ValueWithContext[StringKeyedValue[AnyRef]],
      ValueWithContext[AnyRef],
      MapT[Long, AnyRef],
      String
    ]
    with AggregatorFunctionMixin[MapT] {

  type FlinkCtx =
    KeyedProcessFunction[String, ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef]]#Context

  override def processElement(
      in: ValueWithContext[StringKeyedValue[AnyRef]],
      ctx: FlinkCtx,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    val current: MapT[Long, aggregator.Aggregate] = addElementToState(in, ctx.timestamp(), ctx.timerService(), out)
    val res = computeFinalValue(current, ctx.timestamp()).asInstanceOf[java.util.Map[String, AnyRef]]
    res.put(keyFieldName, in.value.key)
    out.collect(ValueWithContext(res, in.context.clearUserVariables))
  }

}
