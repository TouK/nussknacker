package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.windowing.assigners.{EventTimeSessionWindows, TumblingEventTimeWindows}
import org.apache.flink.streaming.api.windowing.triggers.EventTimeTrigger
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import pl.touk.nussknacker.engine.api.{Context => NkContext, _}
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.flink.api.datastream.DataStreamImplicits.DataStreamExtension
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.flink.util.richflink._
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.ExtendedWindowOperator.OnEventOperatorKeyedStream
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.triggers.ClosingEndEventTrigger
import pl.touk.nussknacker.engine.util.KeyedValue

import scala.collection.immutable.SortedMap
import scala.concurrent.duration.Duration

//TODO: think about merging these with TransformStateFunction and/or PreviousValueFunction
object transformers {

  def slidingTransformer(
      groupBy: LazyParameter[AnyRef],
      groupByParameterName: ParameterName,
      aggregateBy: LazyParameter[AnyRef],
      aggregator: Aggregator,
      windowLength: Duration,
      variableName: String,
      emitWhenEventLeft: Boolean,
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
              new AggregatorFunction[SortedMap](
                aggregator,
                windowLength.toMillis,
                nodeId,
                aggregateBy.returnType,
                typeInfos.storedTypeInfo,
                fctx.convertToEngineRuntimeContext
              )
            else
              new EmitWhenEventLeftAggregatorFunction[SortedMap](
                aggregator,
                windowLength.toMillis,
                nodeId,
                aggregateBy.returnType,
                typeInfos.storedTypeInfo,
                fctx.convertToEngineRuntimeContext
              )
          start
            .groupByWithValue(groupBy, groupByParameterName, aggregateBy, preserveContext)
            .process(aggregatorFunction, typeInfos.returnedValueTypeInfo)
            .setUidAndNameToNodeId(ctx.nodeId)
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
      windowOffset
    )
  }

  def tumblingTransformer(
      groupBy: LazyParameter[AnyRef],
      groupByParameterName: ParameterName,
      aggregateBy: LazyParameter[AnyRef],
      aggregator: Aggregator,
      windowLength: Duration,
      variableName: String,
      tumblingWindowTrigger: TumblingWindowTrigger,
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
            TumblingEventTimeWindows
              .of(java.time.Duration.ofMillis(windowLength.toMillis), java.time.Duration.ofMillis(offsetMillis))

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
                  typeInfos.storedTypeInfo,
                  typeInfos.returnTypeInfo,
                  typeInfos.returnedValueTypeInfo
                )
            case TumblingWindowTrigger.OnEndWithExtraWindow =>
              keyedStream
                // TODO: alignment??
                .process(
                  new EmitExtraWindowWhenNoDataTumblingAggregatorFunction[SortedMap](
                    aggregator,
                    windowLength.toMillis,
                    offsetMillis,
                    nodeId,
                    aggregateBy.returnType,
                    typeInfos.storedTypeInfo,
                    fctx.convertToEngineRuntimeContext
                  )
                )
          }).setUidAndNameToNodeId(ctx.nodeId)
        })
      )
  }

  // Experimental component, API may change in the future
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
          val windowDefinition = EventTimeSessionWindows.withGap(java.time.Duration.ofMillis(sessionTimeout.toMillis))

          (sessionWindowTrigger match {
            case SessionWindowTrigger.OnEvent =>
              keyedStream.extendedEventTriggerWindow(windowDefinition, typeInfos, aggregatingFunction, baseTrigger)
            case SessionWindowTrigger.OnEnd =>
              keyedStream.extendedWindow(windowDefinition, typeInfos, aggregatingFunction, baseTrigger, preserveContext)
          }).setUidAndNameToNodeId(ctx.nodeId)
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

    lazy val storedTypeInfo: TypeInformation[AnyRef] = TypeInformationDetection.instance.forType(storedType)
    lazy val returnTypeInfo: TypeInformation[AnyRef] = TypeInformationDetection.instance.forType(returnType)

    lazy val returnedValueTypeInfo: TypeInformation[ValueWithContext[AnyRef]] =
      ctx.valueWithContextInfo.forType(returnType)

  }

}
