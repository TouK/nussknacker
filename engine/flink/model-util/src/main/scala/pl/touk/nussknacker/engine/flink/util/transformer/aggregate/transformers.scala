package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.annotation.PublicEvolving
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.assigners.{DynamicEventTimeSessionWindows, EventTimeSessionWindows, TumblingEventTimeWindows}
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.triggers.{EventTimeTrigger, Trigger, TriggerResult}
import org.apache.flink.streaming.api.windowing.windows.{TimeWindow, Window}
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.{Context => NkContext, _}
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.util.keyed.{BaseKeyedValueMapper, KeyedValue, StringKeyedValueMapper}

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
          val storedAggregateType = aggregator.computeStoredType(aggregateBy.returnType).valueOr(msg => throw new IllegalArgumentException(msg))
          val expectedType = aggregator.computeOutputType(aggregateBy.returnType).valueOr(msg => throw new IllegalArgumentException(msg))
          val aggregatorFunction =
            if (emitWhenEventLeft)
              new EmitWhenEventLeftAggregatorFunction[SortedMap](aggregator, windowLength.toMillis, nodeId, storedAggregateType)
            else
              new AggregatorFunction[SortedMap](aggregator, windowLength.toMillis, nodeId, storedAggregateType)
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
          val storedAggregateType = aggregator.computeStoredType(aggregateBy.returnType).valueOr(msg => throw new IllegalArgumentException(msg))
          val expectedType = aggregator.computeOutputType(aggregateBy.returnType).valueOr(msg => throw new IllegalArgumentException(msg))
          val keyedStream = start
            .map(new StringKeyedValueMapper(ctx.lazyParameterHelper, keyBy, aggregateBy))
            .keyBy(_.value.key)
          val statefulStream =
            if (emitExtraWindowWhenNoData) {
              keyedStream
                .process(new EmitExtraWindowWhenNoDataTumblingAggregatorFunction[SortedMap](aggregator, windowLength.toMillis, nodeId, storedAggregateType))
            } else {
              keyedStream
                .window(TumblingEventTimeWindows.of(Time.milliseconds(windowLength.toMillis)))
                .aggregate(new UnwrappingAggregateFunction(aggregator, _.value))
            }
          ExplicitUidInOperatorsSupport.setUidIfNeed(explicitUidInStatefulOperators(ctx), ctx.nodeId)(statefulStream)
            .map(_.map(aggregator.alignToExpectedType(_, expectedType)))
        }))

  def sessionWindowTransformer(keyBy: LazyParameter[CharSequence],
                            aggregateBy: LazyParameter[AnyRef],
                            aggregator: Aggregator,
                            windowLength: Duration,
                            endSessionCondition: LazyParameter[java.lang.Boolean],
                            variableName: String,
                            explicitUidInStatefulOperators: FlinkCustomNodeContext => Boolean
                           )(implicit nodeId: NodeId): ContextTransformation =
      ContextTransformation.definedBy(aggregator.toContextTransformation(variableName, aggregateBy))
        .implementedBy(
          FlinkCustomStreamTransformation((start: DataStream[NkContext], ctx: FlinkCustomNodeContext) => {
            val storedAggregateType = aggregator.computeStoredType(aggregateBy.returnType).valueOr(msg => throw new IllegalArgumentException(msg))
            val expectedType = aggregator.computeOutputType(aggregateBy.returnType).valueOr(msg => throw new IllegalArgumentException(msg))
            val keyedStream = start
              .map(new SessionMapper(ctx.lazyParameterHelper, keyBy, aggregateBy, endSessionCondition))
              .keyBy(_.value.key)
            val statefulStream =
                keyedStream
                  .window(EventTimeSessionWindows.withGap(Time.milliseconds(windowLength.toMillis)))
                  .trigger(new EndEventOrTimeTrigger[ValueWithContext[KeyedValue[String, (AnyRef, java.lang.Boolean)]], TimeWindow](EventTimeTrigger.create(), _.value.value._2))
                  .aggregate(new UnwrappingAggregateFunction(aggregator, _.value._1))
            ExplicitUidInOperatorsSupport.setUidIfNeed(explicitUidInStatefulOperators(ctx), ctx.nodeId)(statefulStream)
              .map(_.map(aggregator.alignToExpectedType(_, expectedType)))
          }))

  class EndEventOrTimeTrigger[T, W<:Window](parent: Trigger[_ >: T, W], endFunction: T => Boolean) extends Trigger[T, W] {

    override def onElement(element: T, timestamp: Long, window: W, ctx: Trigger.TriggerContext): TriggerResult = {
      if (endFunction(element)) {
        TriggerResult.FIRE_AND_PURGE
      } else parent.onElement(element, timestamp, window, ctx)
    }

    override def onProcessingTime(time: Long, window: W, ctx: Trigger.TriggerContext): TriggerResult = parent.onProcessingTime(time, window, ctx)

    override def onEventTime(time: Long, window: W, ctx: Trigger.TriggerContext): TriggerResult = parent.onEventTime(time, window, ctx)

    override def clear(window: W, ctx: Trigger.TriggerContext): Unit = parent.clear(window, ctx)

    override def canMerge: Boolean = parent.canMerge

    override def onMerge(window: W, ctx: Trigger.OnMergeContext): Unit = parent.onMerge(window, ctx)
  }

  class SessionMapper(protected val lazyParameterHelper: FlinkLazyParameterFunctionHelper,
                               key: LazyParameter[CharSequence],
                               value: LazyParameter[AnyRef],
                               endSession: LazyParameter[java.lang.Boolean])
    extends BaseKeyedValueMapper[String, (AnyRef, java.lang.Boolean)] {

    private lazy val interpreter = prepareInterpreter(key.map(transformKey), value.product(endSession))

    protected def transformKey(keyValue: CharSequence): String = {
      Option(keyValue).map(_.toString).getOrElse("")
    }

    override protected def interpret(ctx: NkContext): KeyedValue[String, (AnyRef, java.lang.Boolean)] = interpreter(ctx)

  }

}
