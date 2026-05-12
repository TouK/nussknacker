package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.api.common.functions.{OpenContext, RuntimeContext}
import org.apache.flink.api.common.state.{MapState, MapStateDescriptor, ValueState, ValueStateDescriptor}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.TimerService
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.{Context => NkContext, NodeId, ValueWithContext}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.util.keyed.KeyEnricher
import pl.touk.nussknacker.engine.util.KeyedValue

import scala.jdk.CollectionConverters._

class AggregatorFunctionWithMapState(
    protected val aggregator: Aggregator,
    protected val timeWindowLengthMillis: Long,
    override val nodeId: NodeId,
    protected val aggregateElementType: TypingResult,
    protected val aggregateTypeInformation: TypeInformation[AnyRef],
    val convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext
) extends KeyedProcessFunction[AnyRef, ValueWithContext[KeyedValue[AnyRef, AnyRef]], ValueWithContext[AnyRef]]
    with AggregatorFunctionWithMapStateMixin {

  type FlinkCtx =
    KeyedProcessFunction[AnyRef, ValueWithContext[KeyedValue[AnyRef, AnyRef]], ValueWithContext[AnyRef]]#Context

  override def open(openContext: OpenContext): Unit = {
    super.open(openContext)
    initMapState()
  }

  override def processElement(
      value: ValueWithContext[KeyedValue[AnyRef, AnyRef]],
      ctx: FlinkCtx,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    handleNewElementAdded(value, ctx.timestamp(), ctx.timerService(), out)
  }

  override def onTimer(
      timestamp: Long,
      ctx: KeyedProcessFunction[
        AnyRef,
        ValueWithContext[KeyedValue[AnyRef, AnyRef]],
        ValueWithContext[AnyRef]
      ]#OnTimerContext,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    handleOnTimer(timestamp, ctx.timerService())
  }

}

trait AggregatorFunctionWithMapStateMixin extends AggregatorFunctionBase {

  protected def aggregateTypeInformation: TypeInformation[AnyRef]

  @transient
  protected var bucketState: MapState[java.lang.Long, AnyRef] = _

  @transient
  protected var latestEvictionTimeForKey: ValueState[java.lang.Long] = _

  protected def initMapState(): Unit = {
    bucketState = getRuntimeContext.getMapState(
      new MapStateDescriptor[java.lang.Long, AnyRef](
        "buckets",
        TypeInformation.of(classOf[java.lang.Long]),
        aggregateTypeInformation
      )
    )
    latestEvictionTimeForKey = getRuntimeContext.getState[java.lang.Long](
      new ValueStateDescriptor[java.lang.Long]("timers", classOf[java.lang.Long])
    )
  }

  // for extending classes purpose
  protected def handleElementAddedToState(
      newElementInStateTimestamp: Long,
      newElement: aggregator.Element,
      nkCtx: NkContext,
      timeService: TimerService,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {}

  protected def addElementToState(
      value: ValueWithContext[KeyedValue[AnyRef, AnyRef]],
      timestamp: Long,
      timeService: TimerService,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    val bucketTs   = computeTimestampToStore(timestamp)
    val newElement = value.value.value.asInstanceOf[aggregator.Element]

    val currentAggregate = Option(bucketState.get(bucketTs))
      .getOrElse(aggregator.createAccumulator())
      .asInstanceOf[aggregator.Aggregate]

    if (!aggregator.isNeutralForAccumulator(newElement, currentAggregate)) {
      val newAggregate = aggregator.add(newElement, currentAggregate)
      bucketState.put(bucketTs, newAggregate)
    }

    doMoveEvictionTime(bucketTs + timeWindowLengthMillis, timeService)
    handleElementAddedToState(bucketTs, newElement, value.context, timeService, out)
  }

  protected def handleNewElementAdded(
      value: ValueWithContext[KeyedValue[AnyRef, AnyRef]],
      timestamp: Long,
      timeService: TimerService,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    val start = System.nanoTime()
    addElementToState(value, timestamp, timeService, out)
    val finalVal = computeFinalValue(timestamp)
    timeHistogram.update(System.nanoTime() - start)
    out.collect(ValueWithContext(finalVal, KeyEnricher.enrichWithKey(value.context, value.value)))
  }

  protected def computeFinalValue(timestamp: Long): AnyRef = {
    val rangeStart = timestamp - timeWindowLengthMillis + 1
    var count      = 0

    val foldedState = bucketState
      .entries()
      .asScala
      .filter { entry =>
        val ts = entry.getKey.longValue()
        ts >= rangeStart && ts <= timestamp
      }
      .toList
      .sortBy(_.getKey) // maintain timestamp order for order-dependent aggregators (e.g. ListAggregator)
      .foldLeft(aggregator.createAccumulator()) { (acc, entry) =>
        count += 1
        aggregator.merge(acc, entry.getValue)
      }

    retrievedBucketsHistogram.update(count)
    aggregator.alignToExpectedType(aggregator.getResult(foldedState), outputType)
  }

  // Timer management — duplicated from LatelyEvictableStateFunctionMixin (~20 lines, stable code).
  // Not extracted to shared trait to avoid touching LatelyEvictableStateFunctionMixin which is used
  // by unrelated components (UnionWithMemoTransformer, TransformStateTransformer).
  protected def doMoveEvictionTime(time: Long, timeService: TimerService): Unit = {
    val latestEvictionTimeValue = latestEvictionTimeForKey.value()
    val maxEvictionTime = if (latestEvictionTimeValue == null || time > latestEvictionTimeValue) {
      time
    } else {
      latestEvictionTimeValue.longValue()
    }

    if (latestEvictionTimeValue == null) {
      timeService.registerEventTimeTimer(maxEvictionTime)
    }

    latestEvictionTimeForKey.update(maxEvictionTime)
  }

  protected def handleOnTimer(timestamp: Long, timerService: TimerService): Unit = {
    val latestEvictionTimeValue = latestEvictionTimeForKey.value()
    val noLaterEventsArrived    = latestEvictionTimeValue == timestamp
    if (noLaterEventsArrived) {
      evictStates()
    } else if (latestEvictionTimeValue != null) {
      evictOldBuckets(timestamp)
      timerService.registerEventTimeTimer(latestEvictionTimeValue)
    }
  }

  protected def evictOldBuckets(currentWatermark: Long): Unit = {
    val cutoff = currentWatermark - timeWindowLengthMillis + 1 - allowedOutOfOrderMs
    val keysToRemove = bucketState
      .keys()
      .asScala
      .filter(_.longValue() < cutoff)
      .toList
    keysToRemove.foreach(bucketState.remove)
  }

  protected def evictStates(): Unit = {
    bucketState.clear()
    latestEvictionTimeForKey.update(null)
  }

}
