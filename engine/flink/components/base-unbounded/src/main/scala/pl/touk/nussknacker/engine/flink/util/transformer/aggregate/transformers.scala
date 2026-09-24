package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.annotation.PublicEvolving
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.windowing.assigners.{EventTimeSessionWindows, TumblingEventTimeWindows}
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.triggers.EventTimeTrigger
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import pl.touk.nussknacker.engine.api.{Context => NkContext, _}
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.api.typeinformation.{NullableTypeInfo, TypeInformationDetection}
import pl.touk.nussknacker.engine.flink.util.richflink._
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.ExtendedWindowOperator.OnEventOperatorKeyedStream
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.triggers.ClosingEndEventTrigger
import pl.touk.nussknacker.engine.util.KeyedValue

import scala.annotation.nowarn
import scala.concurrent.duration.Duration

//TODO: think about merging these with TransformStateFunction and/or PreviousValueFunction
@PublicEvolving // will be only one version for each method, with explicitUidInStatefulOperators = true
// in the future - see ExplicitUidInOperatorsCompat for more info
object transformers {

  def slidingTransformer(
      groupBy: LazyParameter[AnyRef],
      groupByParameterName: ParameterName,
      aggregateBy: LazyParameter[AnyRef],
      aggregator: Aggregator,
      windowLength: Duration,
      variableName: String,
      emitWhenEventLeft: Boolean,
      explicitUidInStatefulOperators: FlinkCustomNodeContext => Boolean
  )(implicit nodeId: NodeId): ContextTransformation = {
    val preserveContext = !emitWhenEventLeft
    ContextTransformation
      .definedBy(aggregator.toContextTransformation(variableName, preserveContext, aggregateBy, groupBy))
      .implementedBy(
        FlinkCustomStreamTransformation((start: DataStream[NkContext], ctx: FlinkCustomNodeContext) => {
          implicit val fctx: FlinkCustomNodeContext = ctx
          val typeInfos                             = AggregatorTypeInformations(ctx, aggregator, aggregateBy)

          val aggregatorFunction =
            if (preserveContext)
              new AggregatorFunction(
                aggregator,
                windowLength.toMillis,
                nodeId,
                aggregateBy.returnType,
                typeInfos.mapStateStoredTypeInfo,
                fctx.convertToEngineRuntimeContext
              )
            else
              new EmitWhenEventLeftAggregatorFunction(
                aggregator,
                windowLength.toMillis,
                nodeId,
                aggregateBy.returnType,
                typeInfos.mapStateStoredTypeInfo,
                fctx.convertToEngineRuntimeContext
              )
          start
            .groupByWithValue(groupBy, groupByParameterName, aggregateBy, preserveContext)
            .process(aggregatorFunction, typeInfos.returnedValueTypeInfo)
            .setUidWithName(ctx, explicitUidInStatefulOperators)
        })
      )
  }

  def tumblingTransformer(
      groupBy: LazyParameter[AnyRef],
      groupByParameterName: ParameterName,
      aggregateBy: LazyParameter[AnyRef],
      aggregator: Aggregator,
      windowLength: Duration,
      variableName: String,
      windowOffset: Option[Duration] = None
  )(implicit nodeId: NodeId): ContextTransformation = {
    tumblingTransformer(
      groupBy,
      groupByParameterName,
      aggregateBy,
      aggregator,
      windowLength,
      variableName,
      TumblingWindowTrigger.OnEnd,
      ExplicitUidInOperatorsSupport.defaultExplicitUidInStatefulOperators,
      windowOffset
    )
  }

  @nowarn("cat=deprecation")
  def tumblingTransformer(
      groupBy: LazyParameter[AnyRef],
      groupByParameterName: ParameterName,
      aggregateBy: LazyParameter[AnyRef],
      aggregator: Aggregator,
      windowLength: Duration,
      variableName: String,
      tumblingWindowTrigger: TumblingWindowTrigger,
      explicitUidInStatefulOperators: FlinkCustomNodeContext => Boolean,
      windowOffset: Option[Duration]
  )(implicit nodeId: NodeId): ContextTransformation = {
    val preserveContext = tumblingWindowTrigger == TumblingWindowTrigger.OnEvent
    ContextTransformation
      .definedBy(aggregator.toContextTransformation(variableName, preserveContext, aggregateBy, groupBy))
      .implementedBy(
        FlinkCustomStreamTransformation((start: DataStream[NkContext], ctx: FlinkCustomNodeContext) => {
          implicit val fctx: FlinkCustomNodeContext = ctx
          val typeInfos                             = AggregatorTypeInformations(ctx, aggregator, aggregateBy)

          val keyedStream = start
            .groupByWithValue(groupBy, groupByParameterName, aggregateBy, preserveContext)
          val aggregatingFunction =
            new UnwrappingAggregateFunction[AnyRef](aggregator, aggregateBy.returnType, identity)
          val offsetMillis = windowOffset.getOrElse(Duration.Zero).toMillis
          val windowDefinition =
            TumblingEventTimeWindows.of(Time.milliseconds(windowLength.toMillis), Time.milliseconds(offsetMillis))

          (tumblingWindowTrigger match {
            case TumblingWindowTrigger.OnEvent =>
              keyedStream
                .extendedEventTriggerWindow(windowDefinition, typeInfos, aggregatingFunction, EventTimeTrigger.create())
            case TumblingWindowTrigger.OnEnd =>
              keyedStream
                .window(windowDefinition)
                .aggregate(
                  aggregatingFunction,
                  EnrichingWithKeyFunction(fctx),
                  typeInfos.aggregatingStateStoredTypeInfo,
                  typeInfos.returnTypeInfo,
                  typeInfos.returnedValueTypeInfo
                )
            case TumblingWindowTrigger.OnEndWithExtraWindow =>
              keyedStream
                // TODO: alignment??
                .process(
                  new EmitExtraWindowWhenNoDataTumblingAggregatorFunction(
                    aggregator,
                    windowLength.toMillis,
                    offsetMillis,
                    nodeId,
                    aggregateBy.returnType,
                    typeInfos.mapStateStoredTypeInfo,
                    fctx.convertToEngineRuntimeContext
                  )
                )
          }).setUidWithName(ctx, explicitUidInStatefulOperators)
        })
      )
  }

  // Experimental component, API may change in the future
  @nowarn("cat=deprecation")
  def sessionWindowTransformer(
      groupBy: LazyParameter[AnyRef],
      groupByParameterName: ParameterName,
      aggregateBy: LazyParameter[AnyRef],
      aggregator: Aggregator,
      sessionTimeout: Duration,
      endSessionCondition: LazyParameter[java.lang.Boolean],
      sessionWindowTrigger: SessionWindowTrigger,
      variableName: String
  )(implicit nodeId: NodeId): ContextTransformation = {
    val preserveContext = sessionWindowTrigger == SessionWindowTrigger.OnEvent
    ContextTransformation
      .definedBy(aggregator.toContextTransformation(variableName, preserveContext, aggregateBy, groupBy))
      .implementedBy(
        FlinkCustomStreamTransformation((start: DataStream[NkContext], ctx: FlinkCustomNodeContext) => {
          implicit val fctx: FlinkCustomNodeContext = ctx
          val typeInfos                             = AggregatorTypeInformations(ctx, aggregator, aggregateBy)

          val baseTrigger =
            ClosingEndEventTrigger[ValueWithContext[KeyedValue[AnyRef, (AnyRef, java.lang.Boolean)]], TimeWindow](
              EventTimeTrigger.create(),
              _.value.value._2
            )
          val groupByValue = aggregateBy.product(endSessionCondition)

          val keyedStream = start
            .groupByWithValue(groupBy, groupByParameterName, groupByValue, preserveContext)
          val aggregatingFunction =
            new UnwrappingAggregateFunction[(AnyRef, java.lang.Boolean)](aggregator, aggregateBy.returnType, _._1)
          val windowDefinition = EventTimeSessionWindows.withGap(Time.milliseconds(sessionTimeout.toMillis))

          (sessionWindowTrigger match {
            case SessionWindowTrigger.OnEvent =>
              keyedStream.extendedEventTriggerWindow(windowDefinition, typeInfos, aggregatingFunction, baseTrigger)
            case SessionWindowTrigger.OnEnd =>
              keyedStream.extendedWindow(windowDefinition, typeInfos, aggregatingFunction, baseTrigger, preserveContext)
          }).setUidWithName(ctx, ExplicitUidInOperatorsSupport.defaultExplicitUidInStatefulOperators)
        })
      )
  }

  case class AggregatorTypeInformations(
      ctx: FlinkCustomNodeContext,
      aggregator: Aggregator,
      aggregateBy: LazyParameter[AnyRef]
  ) {

    private val returnType = aggregator
      .computeOutputType(aggregateBy.returnType)
      .valueOr(e => throw new IllegalArgumentException(s"Validation error should have happened, got $e"))

    private val storedType = aggregator
      .computeStoredType(aggregateBy.returnType)
      .valueOr(e => throw new IllegalArgumentException(s"Validation error should have happened, got $e"))

    /**
      * For the `MapState` based operators, which write only non-neutral aggregates, and a non-neutral element never
      * produces a null one.
      */
    lazy val mapStateStoredTypeInfo: TypeInformation[AnyRef] = TypeInformationDetection.instance.forType(storedType)

    /**
      * For Flink's AggregatingState, which writes the accumulator after every element, so an aggregate that is still
      * null has to be storable.
      */
    lazy val aggregatingStateStoredTypeInfo: TypeInformation[AnyRef] = new NullableTypeInfo(mapStateStoredTypeInfo)

    /**
      * An aggregate can be null - a numeric one that aggregated nothing, `First`/`Last` when the value they kept was
      * null - and Flink's serializer for the type a return type resolves to cannot write one.
      */
    lazy val returnTypeInfo: TypeInformation[AnyRef] =
      new NullableTypeInfo(TypeInformationDetection.instance.forType(returnType))

    lazy val returnedValueTypeInfo: TypeInformation[ValueWithContext[AnyRef]] =
      ctx.valueWithContextInfo.forType(returnTypeInfo)

  }

}
