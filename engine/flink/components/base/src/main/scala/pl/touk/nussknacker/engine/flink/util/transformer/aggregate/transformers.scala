package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.annotation.PublicEvolving
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.windowing.assigners.{EventTimeSessionWindows, TumblingEventTimeWindows}
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.triggers.EventTimeTrigger
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.{NodeId, Context => NkContext, _}
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.util.richflink._
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.OnEventTriggerWindowOperator.OnEventOperatorKeyedStream
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.triggers.ClosingEndEventTrigger
import pl.touk.nussknacker.engine.util.KeyedValue

import scala.collection.immutable.SortedMap
import scala.concurrent.duration.Duration

//TODO: think about merging these with TransformStateFunction and/or PreviousValueFunction
@PublicEvolving // will be only one version for each method, with explicitUidInStatefulOperators = true
// in the future - see ExplicitUidInOperatorsCompat for more info
object transformers {

  def slidingTransformer(groupBy: LazyParameter[CharSequence],
                         aggregateBy: LazyParameter[AnyRef],
                         aggregator: Aggregator,
                         windowLength: Duration,
                         variableName: String)(implicit nodeId: NodeId): ContextTransformation =
    slidingTransformer(groupBy, aggregateBy, aggregator, windowLength, variableName, emitWhenEventLeft = false,
      ExplicitUidInOperatorsSupport.defaultExplicitUidInStatefulOperators)

  def slidingTransformer(groupBy: LazyParameter[CharSequence],
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
              new EmitWhenEventLeftAggregatorFunction[SortedMap](aggregator, windowLength.toMillis, nodeId, aggregateBy.returnType, typeInfos.storedTypeInfo, fctx.convertToEngineRuntimeContext)
            else
              new AggregatorFunction[SortedMap](aggregator, windowLength.toMillis, nodeId, aggregateBy.returnType, typeInfos.storedTypeInfo, fctx.convertToEngineRuntimeContext)
          start
            .groupByWithValue(groupBy, aggregateBy)
            .process(aggregatorFunction, typeInfos.returnedValueTypeInfo)
            .setUidWithName(ctx, explicitUidInStatefulOperators)
        }))
  }

  def tumblingTransformer(groupBy: LazyParameter[CharSequence],
                          aggregateBy: LazyParameter[AnyRef],
                          aggregator: Aggregator,
                          windowLength: Duration,
                          variableName: String)(implicit nodeId: NodeId): ContextTransformation = {
    tumblingTransformer(groupBy, aggregateBy, aggregator, windowLength, variableName, TumblingWindowTrigger.OnEnd,
      ExplicitUidInOperatorsSupport.defaultExplicitUidInStatefulOperators)
  }

  def tumblingTransformer(groupBy: LazyParameter[CharSequence],
                          aggregateBy: LazyParameter[AnyRef],
                          aggregator: Aggregator,
                          windowLength: Duration,
                          variableName: String,
                          tumblingWindowTrigger: TumblingWindowTrigger,
                          explicitUidInStatefulOperators: FlinkCustomNodeContext => Boolean,
                          windowOffsetProvider: WindowOffsetProvider = WindowOffsetProvider.DailyWindowsOffsetDependingOnTimezone
                         )(implicit nodeId: NodeId): ContextTransformation =
    ContextTransformation.definedBy(aggregator.toContextTransformation(variableName,
      emitContext = tumblingWindowTrigger == TumblingWindowTrigger.OnEvent, aggregateBy))
      .implementedBy(
        FlinkCustomStreamTransformation((start: DataStream[NkContext], ctx: FlinkCustomNodeContext) => {
          implicit val fctx: FlinkCustomNodeContext = ctx
          val typeInfos = AggregatorTypeInformations(ctx, aggregator, aggregateBy)

          val keyedStream = start
            .groupByWithValue(groupBy, aggregateBy)
          val aggregatingFunction = new UnwrappingAggregateFunction[AnyRef](aggregator, aggregateBy.returnType, identity)
          val offsetMillis = windowOffsetProvider.offset(windowLength).toMillis
          val windowDefinition = TumblingEventTimeWindows.of(Time.milliseconds(windowLength.toMillis), Time.milliseconds(offsetMillis))

          (tumblingWindowTrigger match {
            case TumblingWindowTrigger.OnEvent =>
              keyedStream
                .eventTriggerWindow(windowDefinition, typeInfos, aggregatingFunction, EventTimeTrigger.create())
            case TumblingWindowTrigger.OnEnd =>
              keyedStream
                .window(windowDefinition)
                .aggregate(aggregatingFunction,
                  EnrichingWithKeyFunction(fctx), typeInfos.storedTypeInfo, typeInfos.returnTypeInfo, typeInfos.returnedValueTypeInfo)
            case TumblingWindowTrigger.OnEndWithExtraWindow =>
              keyedStream
                //TODO: alignment??
                .process(new EmitExtraWindowWhenNoDataTumblingAggregatorFunction[SortedMap](aggregator, windowLength.toMillis, nodeId, aggregateBy.returnType, typeInfos.storedTypeInfo, fctx.convertToEngineRuntimeContext))
          }).setUidWithName(ctx, explicitUidInStatefulOperators)
        }))

  //Experimental component, API may change in the future
  def sessionWindowTransformer(groupBy: LazyParameter[CharSequence],
                               aggregateBy: LazyParameter[AnyRef],
                               aggregator: Aggregator,
                               sessionTimeout: Duration,
                               endSessionCondition: LazyParameter[java.lang.Boolean],
                               sessionWindowTrigger: SessionWindowTrigger,
                               variableName: String
                              )(implicit nodeId: NodeId): ContextTransformation =
    ContextTransformation.definedBy(aggregator.toContextTransformation(variableName, emitContext = sessionWindowTrigger == SessionWindowTrigger.OnEvent, aggregateBy))
      .implementedBy(
        FlinkCustomStreamTransformation((start: DataStream[NkContext], ctx: FlinkCustomNodeContext) => {
          implicit val fctx: FlinkCustomNodeContext = ctx
          val typeInfos = AggregatorTypeInformations(ctx, aggregator, aggregateBy)

          val baseTrigger =
            ClosingEndEventTrigger[ValueWithContext[KeyedValue[String, (AnyRef, java.lang.Boolean)]], TimeWindow](EventTimeTrigger.create(), _.value.value._2)
          val groupByValue = aggregateBy.product(endSessionCondition)

          val keyedStream = start
            .groupByWithValue(groupBy, groupByValue)
          val aggregatingFunction = new UnwrappingAggregateFunction[(AnyRef, java.lang.Boolean)](aggregator, aggregateBy.returnType, _._1)
          val windowDefinition = EventTimeSessionWindows.withGap(Time.milliseconds(sessionTimeout.toMillis))

          (sessionWindowTrigger match {
            case SessionWindowTrigger.OnEvent =>
              keyedStream.eventTriggerWindow(windowDefinition, typeInfos, aggregatingFunction, baseTrigger)
            case SessionWindowTrigger.OnEnd =>
              keyedStream.window(windowDefinition)
                .trigger(baseTrigger)
                .aggregate(
                  new UnwrappingAggregateFunction[(AnyRef, java.lang.Boolean)](aggregator, aggregateBy.returnType, _._1),
                  EnrichingWithKeyFunction(fctx), typeInfos.storedTypeInfo, typeInfos.returnTypeInfo, typeInfos.returnedValueTypeInfo)
          }).setUidWithName(ctx, ExplicitUidInOperatorsSupport.defaultExplicitUidInStatefulOperators)
        }))

  case class AggregatorTypeInformations(ctx: FlinkCustomNodeContext, aggregator: Aggregator, aggregateBy: LazyParameter[AnyRef]) {

    private val detection = ctx.typeInformationDetection

    private val returnType = aggregator.computeOutputType(aggregateBy.returnType)
      .valueOr(e => throw new IllegalArgumentException(s"Validation error should have happened, got $e"))
    private val storedType = aggregator.computeStoredType(aggregateBy.returnType)
      .valueOr(e => throw new IllegalArgumentException(s"Validation error should have happened, got $e"))

    lazy val storedTypeInfo: TypeInformation[AnyRef] = detection.forType(storedType)
    lazy val returnTypeInfo: TypeInformation[AnyRef] = detection.forType(returnType)

    lazy val returnedValueTypeInfo: TypeInformation[ValueWithContext[AnyRef]] = ctx.valueWithContextInfo.forType(returnType)

  }

}
