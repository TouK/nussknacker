package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.annotation.PublicEvolving
import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.{Context => NkContext, _}
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.api.state.LatelyEvictableStateFunction
import pl.touk.nussknacker.engine.flink.util.keyed.{StringKeyedValue, StringKeyedValueMapper}

import scala.collection.immutable.TreeMap
import scala.concurrent.duration.Duration

//TODO: think about merging these with TransformStateFunction and/or PreviousValueFunction
@PublicEvolving // will be only one version for each method, with explicitUidInStatefulOperators = true
                // in the future - see ExplicitUidInOperatorsCompat for more info
object transformers {

  def slidingTransformer(keyBy: LazyParameter[CharSequence],
                         aggregateBy: LazyParameter[AnyRef],
                         aggregator: Aggregator,
                         windowLength: Duration,
                         variableName: String)(implicit nodeId: NodeId): ContextTransformation =
    slidingTransformer(keyBy, aggregateBy, aggregator, windowLength, variableName,
      ExplicitUidInOperatorsSupport.defaultExplicitUidInStatefulOperators)

  def slidingTransformer(keyBy: LazyParameter[CharSequence],
                         aggregateBy: LazyParameter[AnyRef],
                         aggregator: Aggregator,
                         windowLength: Duration,
                         variableName: String,
                         explicitUidInStatefulOperators: FlinkCustomNodeContext => Boolean
                        )(implicit nodeId: NodeId): ContextTransformation =
    ContextTransformation.definedBy(aggregator.toContextTransformation(variableName, aggregateBy))
      .implementedBy(
        FlinkCustomStreamTransformation((start: DataStream[NkContext], ctx: FlinkCustomNodeContext) => {
          ExplicitUidInOperatorsSupport.setUidIfNeed(explicitUidInStatefulOperators(ctx), ctx.nodeId)(
            start
              .map(new StringKeyedValueMapper(ctx.lazyParameterHelper, keyBy, aggregateBy))
              .keyBy(_.value.key)
              .process(new AggregatorFunction(aggregator, windowLength.toMillis, ctx.nodeId)))
        }))

  def tumblingTransformer(keyBy: LazyParameter[CharSequence],
                          aggregateBy: LazyParameter[AnyRef],
                          aggregator: Aggregator,
                          windowLength: Duration,
                          variableName: String)(implicit nodeId: NodeId): ContextTransformation = {
    tumblingTransformer(keyBy, aggregateBy, aggregator, windowLength, variableName,
      ExplicitUidInOperatorsSupport.defaultExplicitUidInStatefulOperators)
  }

  def tumblingTransformer(keyBy: LazyParameter[CharSequence],
                          aggregateBy: LazyParameter[AnyRef],
                          aggregator: Aggregator,
                          windowLength: Duration,
                          variableName: String,
                          explicitUidInStatefulOperators: FlinkCustomNodeContext => Boolean
                         )(implicit nodeId: NodeId): ContextTransformation =
    ContextTransformation.definedBy(aggregator.toContextTransformation(variableName, aggregateBy))
      .implementedBy(
        FlinkCustomStreamTransformation((start: DataStream[NkContext], ctx: FlinkCustomNodeContext) => {
          ExplicitUidInOperatorsSupport.setUidIfNeed(explicitUidInStatefulOperators(ctx), ctx.nodeId)(start
            .map(new StringKeyedValueMapper(ctx.lazyParameterHelper, keyBy, aggregateBy))
            .keyBy(_.value.key)
            .window(TumblingProcessingTimeWindows.of(Time.milliseconds(windowLength.toMillis)))
            // FIXME: It can't work - we should aggregate on unwrapped value  
            .aggregate(aggregator
              // this casting seems to be needed for Flink to infer TypeInformation correctly....
              .asInstanceOf[org.apache.flink.api.common.functions.AggregateFunction[ValueWithContext[StringKeyedValue[AnyRef]], AnyRef, ValueWithContext[AnyRef]]]))

        }))

}

//This function behaves a bit like SlidingWindow with slide 1min, trigger on each event, but we
//do reduce on each emit and store partial aggregations for each minute, instead of storing aggregates for each slide
//TODO: figure out if it's more convenient/faster to just use SlidingWindow with 60s slide and appropriate trigger?
class AggregatorFunction(aggregator: Aggregator, lengthInMillis: Long, nodeId: String)
  extends LatelyEvictableStateFunction[ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef], TreeMap[Long, AnyRef]] {

  type FlinkCtx = KeyedProcessFunction[String, ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef]]#Context

  //TODO make it configurable
  private val minimalResolutionMs = 60000L

  override def processElement(value: ValueWithContext[StringKeyedValue[AnyRef]], ctx: FlinkCtx, out: Collector[ValueWithContext[AnyRef]]): Unit = {

    moveEvictionTime(lengthInMillis, ctx)

    val newElement = value.value.value.asInstanceOf[aggregator.Element]
    val newState: TreeMap[Long, aggregator.Aggregate] = computeNewState(newElement, ctx)

    state.update(newState)

    val finalVal: AnyRef = computeFinalValue(newState)
    out.collect(ValueWithContext(finalVal, value.context))
  }

  private def computeFinalValue(newState: TreeMap[Long, aggregator.Aggregate]) = {
    val foldedState = newState.values.reduce(aggregator.merge)
    aggregator.getResult(foldedState)
  }

  private def computeNewState(newValue: aggregator.Element, ctx: FlinkCtx): TreeMap[Long, aggregator.Aggregate] = {

    val current: TreeMap[Long, aggregator.Aggregate] = currentState(ctx)

    val timestampToUse = computeTimestampToStore(ctx)

    val currentAggregate = current.getOrElse(timestampToUse, aggregator.createAccumulator())
    val newAggregate = aggregator.add(newValue, currentAggregate).asInstanceOf[aggregator.Aggregate]

    current.updated(timestampToUse, newAggregate)
  }

  private def computeTimestampToStore(ctx: FlinkCtx): Long = {
    (ctx.timestamp() / minimalResolutionMs) * minimalResolutionMs
  }

  private def currentState(ctx: FlinkCtx): TreeMap[Long, aggregator.Aggregate] = {
    val currentState = Option(state.value().asInstanceOf[TreeMap[Long, aggregator.Aggregate]]).getOrElse(TreeMap[Long, aggregator.Aggregate]()(Ordering.Long))
    currentState.from(ctx.timestamp() - lengthInMillis)
  }

  override protected def stateDescriptor: ValueStateDescriptor[TreeMap[Long, AnyRef]]
  = new ValueStateDescriptor[TreeMap[Long, AnyRef]]("state", classOf[TreeMap[Long, AnyRef]])
}



