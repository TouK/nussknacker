package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.annotation.PublicEvolving
import org.apache.flink.api.common.typeinfo.TypeInformation
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
import pl.touk.nussknacker.engine.flink.util.keyed.{KeyedValue, StringKeyedValue}
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
    ContextTransformation.definedBy(aggregator.toContextTransformation(variableName, !emitWhenEventLeft, aggregateBy))
      .implementedBy(
        FlinkCustomStreamTransformation((start: DataStream[NkContext], ctx: FlinkCustomNodeContext) => {
          implicit val fctx: FlinkCustomNodeContext = ctx
          val typeInfos = AggregatorTypeInformations(ctx, aggregator, aggregateBy)

          val aggregatorFunction =
            if (emitWhenEventLeft)
              new EmitWhenEventLeftAggregatorFunction[SortedMap](aggregator, windowLength.toMillis, nodeId, aggregateBy.returnType, typeInfos.storedTypeInfo)
            else
              new AggregatorFunction[SortedMap](aggregator, windowLength.toMillis, nodeId, aggregateBy.returnType, typeInfos.storedTypeInfo)
          start
            .keyByWithValue(keyBy, _ => aggregateBy)
            .process(aggregatorFunction)
            .setUidWithName(ctx, explicitUidInStatefulOperators)
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
    // TODO: to be consistent with sliding window we should probably forward context of variables for tumblingWindowTrigger == TumblingWindowTrigger.OnEvent
    ContextTransformation.definedBy(aggregator.toContextTransformation(variableName, emitContext = false, aggregateBy))
      .implementedBy(
        FlinkCustomStreamTransformation((start: DataStream[NkContext], ctx: FlinkCustomNodeContext) => {
          implicit val fctx: FlinkCustomNodeContext = ctx
          val typeInfos = AggregatorTypeInformations(ctx, aggregator, aggregateBy)

          val keyedStream = start
            .keyByWithValue(keyBy, _ => aggregateBy)
          (tumblingWindowTrigger match {
            case TumblingWindowTrigger.OnEvent =>
              keyedStream
                 .window(TumblingEventTimeWindows.of(Time.milliseconds(windowLength.toMillis)))
                 .trigger(FireOnEachEvent[AnyRef, TimeWindow](EventTimeTrigger.create()))
                 .aggregate(
                   new UnwrappingAggregateFunction[AnyRef](aggregator, aggregateBy.returnType, identity),
                   new EnrichingWithKeyFunction)(typeInfos.storedTypeInfo, typeInfos.returnTypeInfo, typeInfos.returnedValueTypeInfo)
            case TumblingWindowTrigger.OnEnd =>
              keyedStream
                 .window(TumblingEventTimeWindows.of(Time.milliseconds(windowLength.toMillis)))
                 .aggregate(
                   new UnwrappingAggregateFunction[AnyRef](aggregator, aggregateBy.returnType, identity),
                   new EnrichingWithKeyFunction)(typeInfos.storedTypeInfo, typeInfos.returnTypeInfo, typeInfos.returnedValueTypeInfo)
            case TumblingWindowTrigger.OnEndWithExtraWindow =>
              keyedStream
                 //TODO: alignment??
                 .process(new EmitExtraWindowWhenNoDataTumblingAggregatorFunction[SortedMap](aggregator, windowLength.toMillis, nodeId, aggregateBy.returnType, typeInfos.storedTypeInfo))
          }).setUidWithName(ctx, explicitUidInStatefulOperators)
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
    // TODO: to be consistent with sliding window we should probably forward context of variables for tumblingWindowTrigger == SessionWindowTrigger.OnEnd
    ContextTransformation.definedBy(aggregator.toContextTransformation(variableName, emitContext = false, aggregateBy))
      .implementedBy(
        FlinkCustomStreamTransformation((start: DataStream[NkContext], ctx: FlinkCustomNodeContext) => {
          implicit val fctx: FlinkCustomNodeContext = ctx
          val typeInfos = AggregatorTypeInformations(ctx, aggregator, aggregateBy)

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
            .aggregate(
              new UnwrappingAggregateFunction[(AnyRef, java.lang.Boolean)](aggregator, aggregateBy.returnType, _._1),
              new EnrichingWithKeyFunction)(typeInfos.storedTypeInfo, typeInfos.returnTypeInfo, typeInfos.returnedValueTypeInfo)
            .setUidWithName(ctx, ExplicitUidInOperatorsSupport.defaultExplicitUidInStatefulOperators)
        }))

  case class AggregatorTypeInformations(ctx: FlinkCustomNodeContext, aggregator: Aggregator, aggregateBy: LazyParameter[AnyRef]) {

    private val detection = ctx.typeInformationDetection

    private val vctx = ctx.validationContext.left.get

    private val returnType = aggregator.computeOutputType(aggregateBy.returnType)
      .valueOr(e => throw new IllegalArgumentException(s"Validation error should have happened, got $e"))
    private val storedType = aggregator.computeStoredType(aggregateBy.returnType)
      .valueOr(e => throw new IllegalArgumentException(s"Validation error should have happened, got $e"))

    lazy val storedTypeInfo: TypeInformation[AnyRef] = detection.forType(storedType)
    lazy val returnTypeInfo: TypeInformation[AnyRef] = detection.forType(returnType)

    lazy val contextTypeInfo: TypeInformation[NkContext] = detection.forContext(vctx)
    lazy val returnedValueTypeInfo: TypeInformation[ValueWithContext[AnyRef]] = detection.forValueWithContext(vctx, returnType)

  }

}
