package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.annotation.PublicEvolving
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.{Context => NkContext, _}
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.util.keyed.StringKeyedValueMapper

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
    slidingTransformer(keyBy, aggregateBy, aggregator, windowLength, variableName, emitWhenEventLeft = false,
      ExplicitUidInOperatorsSupport.defaultExplicitUidInStatefulOperators)

  def slidingTransformer(keyBy: LazyParameter[CharSequence],
                         aggregateBy: LazyParameter[AnyRef],
                         aggregator: Aggregator,
                         windowLength: Duration,
                         variableName: String,
                         emitWhenEventLeft: Boolean,
                         explicitUidInStatefulOperators: FlinkCustomNodeContext => Boolean
                        )(implicit nodeId: NodeId): ContextTransformation = {
    ContextTransformation.definedBy(aggregator.toContextTransformation(variableName, aggregateBy))
      .implementedBy(
        FlinkCustomStreamTransformation((start: DataStream[NkContext], ctx: FlinkCustomNodeContext) => {
          val expectedType = aggregator.computeOutputType(aggregateBy.returnType).valueOr(msg => throw new IllegalArgumentException(msg))
          val aggregatorFunction =
            if (emitWhenEventLeft)
              new EmitWhenEventLeftAggregatorFunction(aggregator, windowLength.toMillis, nodeId)
            else
              new AggregatorFunction(aggregator, windowLength.toMillis, nodeId)
          val statefulStream = start
            .map(new StringKeyedValueMapper(ctx.lazyParameterHelper, keyBy, aggregateBy))
            .keyBy(_.value.key)
            .process(aggregatorFunction)
          ExplicitUidInOperatorsSupport.setUidIfNeed(explicitUidInStatefulOperators(ctx), ctx.nodeId)(statefulStream)
            .map(_.map(aggregator.alignToExpectedType(_, expectedType)))
        }))
  }

  def tumblingTransformer(keyBy: LazyParameter[CharSequence],
                          aggregateBy: LazyParameter[AnyRef],
                          aggregator: Aggregator,
                          windowLength: Duration,
                          variableName: String)(implicit nodeId: NodeId): ContextTransformation = {
    tumblingTransformer(keyBy, aggregateBy, aggregator, windowLength, variableName, emitExtraWindowWhenNoData = false,
      ExplicitUidInOperatorsSupport.defaultExplicitUidInStatefulOperators)
  }

  def tumblingTransformer(keyBy: LazyParameter[CharSequence],
                          aggregateBy: LazyParameter[AnyRef],
                          aggregator: Aggregator,
                          windowLength: Duration,
                          variableName: String,
                          emitExtraWindowWhenNoData: Boolean,
                          explicitUidInStatefulOperators: FlinkCustomNodeContext => Boolean
                         )(implicit nodeId: NodeId): ContextTransformation =
    ContextTransformation.definedBy(aggregator.toContextTransformation(variableName, aggregateBy))
      .implementedBy(
        FlinkCustomStreamTransformation((start: DataStream[NkContext], ctx: FlinkCustomNodeContext) => {
          val expectedType = aggregator.computeOutputType(aggregateBy.returnType).valueOr(msg => throw new IllegalArgumentException(msg))
          val keyedStream = start
            .map(new StringKeyedValueMapper(ctx.lazyParameterHelper, keyBy, aggregateBy))
            .keyBy(_.value.key)
          val statefulStream =
            if (emitExtraWindowWhenNoData) {
              keyedStream
                .process(new EmitExtraWindowWhenNoDataTumblingAggregatorFunction(aggregator, windowLength.toMillis, nodeId))
            } else {
              keyedStream
                .window(TumblingEventTimeWindows.of(Time.milliseconds(windowLength.toMillis)))
                .aggregate(new UnwrappingAggregateFunction(aggregator))
            }
          ExplicitUidInOperatorsSupport.setUidIfNeed(explicitUidInStatefulOperators(ctx), ctx.nodeId)(statefulStream)
            .map(_.map(aggregator.alignToExpectedType(_, expectedType)))
        }))

}