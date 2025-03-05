package pl.touk.nussknacker.engine.flink.util.transformer.join

import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.co.CoProcessFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.api.state.LatelyEvictableStateCoFunction
import pl.touk.nussknacker.engine.flink.util.keyed.StringKeyedValue
import pl.touk.nussknacker.engine.flink.util.orderedmap.FlinkRangeMap
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.{Aggregator, AggregatorFunctionMixin}

import scala.language.higherKinds

class SingleSideJoinAggregatorFunction[MapT[K, V]](
    protected val aggregator: Aggregator,
    protected val timeWindowLengthMillis: Long,
    override val nodeId: NodeId,
    protected val aggregateElementType: TypingResult,
    override protected val aggregateTypeInformation: TypeInformation[AnyRef],
    val convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext
)(implicit override val rangeMap: FlinkRangeMap[MapT])
    extends LatelyEvictableStateCoFunction[ValueWithContext[String], ValueWithContext[
      StringKeyedValue[AnyRef]
    ], ValueWithContext[AnyRef], MapT[Long, AnyRef]]
    with AggregatorFunctionMixin[MapT] {

  type FlinkCtx = CoProcessFunction[ValueWithContext[String], ValueWithContext[
    StringKeyedValue[AnyRef]
  ], ValueWithContext[AnyRef]]#Context

  override def processElement1(
      in1: ValueWithContext[String],
      ctx: FlinkCtx,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    val current: MapT[Long, aggregator.Aggregate] = readStateOrInitial()
    val finalVal                                  = computeFinalValue(current, ctx.timestamp())
    out.collect(ValueWithContext(finalVal, in1.context))
  }

  override def processElement2(
      in2: ValueWithContext[StringKeyedValue[AnyRef]],
      ctx: FlinkCtx,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    addElementToState(in2, ctx.timestamp(), ctx.timerService(), out)
  }

}
