package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.annotation.PublicEvolving
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.assigners.{EventTimeSessionWindows, TumblingEventTimeWindows}
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.triggers.EventTimeTrigger
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.{Context => NkContext, _}
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.util.keyed.KeyedValue
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.triggers.{ClosingEndEventTrigger, FireOnEachEvent}
import pl.touk.nussknacker.engine.flink.util.transformer.richflink._

import scala.collection.immutable.SortedMap
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
          implicit val fctx: FlinkCustomNodeContext = ctx

          val aggregatorFunction =
            if (emitWhenEventLeft)
              new EmitWhenEventLeftAggregatorFunction[SortedMap](aggregator, windowLength.toMillis, nodeId, aggregateBy.returnType)
            else
              new AggregatorFunction[SortedMap](aggregator, windowLength.toMillis, nodeId, aggregateBy.returnType)
          start
            .keyByWithValue(keyBy, _ => aggregateBy)
            .process(aggregatorFunction)
            .setUid(ctx, explicitUidInStatefulOperators)
        }))
  }

  def tumblingTransformer(keyBy: LazyParameter[CharSequence],
                          aggregateBy: LazyParameter[AnyRef],
                          aggregator: Aggregator,
                          windowLength: Duration,
                          variableName: String)(implicit nodeId: NodeId): ContextTransformation = {
    tumblingTransformer(keyBy, aggregateBy, aggregator, windowLength, variableName, TumblingWindowTrigger.OnEnd,
      ExplicitUidInOperatorsSupport.defaultExplicitUidInStatefulOperators)
  }

  def tumblingTransformer(keyBy: LazyParameter[CharSequence],
                          aggregateBy: LazyParameter[AnyRef],
                          aggregator: Aggregator,
                          windowLength: Duration,
                          variableName: String,
                          tumblingWindowTrigger: TumblingWindowTrigger,
                          explicitUidInStatefulOperators: FlinkCustomNodeContext => Boolean
                         )(implicit nodeId: NodeId): ContextTransformation =
    ContextTransformation.definedBy(aggregator.toContextTransformation(variableName, aggregateBy))
      .implementedBy(
        FlinkCustomStreamTransformation((start: DataStream[NkContext], ctx: FlinkCustomNodeContext) => {
          implicit val fctx: FlinkCustomNodeContext = ctx

          val keyedStream = start
            .keyByWithValue(keyBy, _ => aggregateBy)
          (tumblingWindowTrigger match {
            case TumblingWindowTrigger.OnEvent =>
              keyedStream
                 .window(TumblingEventTimeWindows.of(Time.milliseconds(windowLength.toMillis)))
                 .trigger(FireOnEachEvent[AnyRef, TimeWindow](EventTimeTrigger.create()))
                 .aggregate(new UnwrappingAggregateFunction(aggregator, aggregateBy.returnType, _.value, LastContextStrategy))
            case TumblingWindowTrigger.OnEnd =>
              keyedStream
                 .window(TumblingEventTimeWindows.of(Time.milliseconds(windowLength.toMillis)))
                //TODO: which context do we want to emit?
                 .aggregate(new UnwrappingAggregateFunction(aggregator, aggregateBy.returnType, _.value, FirstContextStrategy))
            case TumblingWindowTrigger.OnEndWithExtraWindow =>
              keyedStream
                 //TODO: alignment??
                 .process(new EmitExtraWindowWhenNoDataTumblingAggregatorFunction[SortedMap](aggregator, windowLength.toMillis, nodeId, aggregateBy.returnType))
          }).setUid(ctx, explicitUidInStatefulOperators)
        }))

  //Experimental component, API may change in the future
  def sessionWindowTransformer(keyBy: LazyParameter[CharSequence],
                               aggregateBy: LazyParameter[AnyRef],
                               aggregator: Aggregator,
                               sessionTimeout: Duration,
                               endSessionCondition: LazyParameter[java.lang.Boolean],
                               sessionWindowTrigger: SessionWindowTrigger,
                               variableName: String
                              )(implicit nodeId: NodeId): ContextTransformation =
    ContextTransformation.definedBy(aggregator.toContextTransformation(variableName, aggregateBy))
      .implementedBy(
        FlinkCustomStreamTransformation((start: DataStream[NkContext], ctx: FlinkCustomNodeContext) => {
          implicit val fctx: FlinkCustomNodeContext = ctx
          val baseTrigger =
            ClosingEndEventTrigger[ValueWithContext[KeyedValue[String, (AnyRef, java.lang.Boolean)]], TimeWindow](EventTimeTrigger.create(), _.value.value._2)
          val trigger = sessionWindowTrigger match {
            case SessionWindowTrigger.OnEvent => FireOnEachEvent(baseTrigger)
            case SessionWindowTrigger.OnEnd => baseTrigger
          }
          start
            .keyByWithValue(keyBy, _.product(aggregateBy, endSessionCondition))
            .window(EventTimeSessionWindows.withGap(Time.milliseconds(sessionTimeout.toMillis)))
            .trigger(trigger)
            .aggregate(new UnwrappingAggregateFunction(aggregator, aggregateBy.returnType, _.value._1, LastContextStrategy))
            .setUid(ctx, ExplicitUidInOperatorsSupport.defaultExplicitUidInStatefulOperators)
        }))



}
