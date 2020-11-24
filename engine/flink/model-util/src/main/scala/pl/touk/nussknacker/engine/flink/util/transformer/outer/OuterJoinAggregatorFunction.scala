package pl.touk.nussknacker.engine.flink.util.transformer.outer

import org.apache.flink.streaming.api.functions.co.CoProcessFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.flink.api.state.LatelyEvictableStateCoFunction
import pl.touk.nussknacker.engine.flink.util.keyed.StringKeyedValue
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.{Aggregator, AggregatorFunctionMixin}

import scala.collection.immutable.TreeMap

class OuterJoinAggregatorFunction(protected val aggregator: Aggregator, protected val timeWindowLengthMillis: Long, override val nodeId: NodeId)
  extends LatelyEvictableStateCoFunction[ValueWithContext[String], ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef], TreeMap[Long, AnyRef]]
    with AggregatorFunctionMixin {

  type FlinkCtx = CoProcessFunction[ValueWithContext[String], ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef]]#Context

  override def processElement1(in1: ValueWithContext[String], ctx: FlinkCtx, out: Collector[ValueWithContext[AnyRef]]): Unit = {
    val current: TreeMap[Long, aggregator.Aggregate] = readStateOrInitial()
    val finalVal = computeFinalValue(current, ctx.timestamp())
    out.collect(ValueWithContext(finalVal, in1.context))
  }

  override def processElement2(in2: ValueWithContext[StringKeyedValue[AnyRef]], ctx: FlinkCtx, out: Collector[ValueWithContext[AnyRef]]): Unit = {
    addElementToState(in2, ctx.timestamp(), ctx.timerService(), out)
  }

}