package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import cats.data.NonEmptyList
import org.apache.flink.api.common.functions.{OpenContext, RichFunction, RuntimeContext}
import org.apache.flink.api.common.state.{MapState, MapStateDescriptor, ValueState, ValueStateDescriptor}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.ListTypeInfo
import org.apache.flink.streaming.api.TimerService
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.{Context => NkContext, NodeId, ValueWithContext}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.util.keyed.KeyEnricher
import pl.touk.nussknacker.engine.util
import pl.touk.nussknacker.engine.util.KeyedValue
import pl.touk.nussknacker.engine.util.metrics.{MetricIdentifier, MetricsProviderForScenario}
import pl.touk.nussknacker.engine.util.metrics.common.naming.nodeIdTag

import java.lang
import scala.collection.Searching._
import scala.collection.View
import scala.jdk.CollectionConverters._

// This is the real SlidingWindow with slide = 1min - moving with time for each key. It reduce on each emit and store
// partial aggregations for each minute, instead of storing aggregates for each slide. This implementation doesn't emit event
// when some element left the slide. If you want to handle this situation, you should use `EmitWhenEventLeftAggregatorFunction`
// Flinks SlidingWindows are something that is often called HoppingWindow. They consume a lot of memory because each element
// is stored in multiple windows with different offsets.
// NOTE: it would be much cleaner if we evaluated aggregateBy here. However, FLINK-10250 prevents us from doing this and we *have* to compute it before
class AggregatorFunction(
    protected val aggregator: Aggregator,
    protected val timeWindowLengthMillis: Long,
    override val nodeId: NodeId,
    protected val aggregateElementType: TypingResult,
    protected val aggregateTypeInformation: TypeInformation[AnyRef],
    val convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext
) extends KeyedProcessFunction[AnyRef, ValueWithContext[KeyedValue[AnyRef, AnyRef]], ValueWithContext[AnyRef]]
    with AggregatorFunctionMixin {

  type FlinkCtx =
    KeyedProcessFunction[AnyRef, ValueWithContext[KeyedValue[AnyRef, AnyRef]], ValueWithContext[AnyRef]]#Context

  override def open(openContext: OpenContext): Unit = {
    super.open(openContext)
    initState()
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

trait AggregatorFunctionMixin extends RichFunction {

  protected def convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext

  protected lazy val engineRuntimeContext: EngineRuntimeContext = convertToEngineRuntimeContext(getRuntimeContext)

  def nodeId: NodeId

  protected def name: String = "aggregator"

  protected def tags: Map[String, String] = Map(nodeIdTag -> nodeId.id)

  protected lazy val metricsProvider: MetricsProviderForScenario = engineRuntimeContext.metricsProvider

  protected lazy val timeHistogram: util.metrics.Histogram =
    metricsProvider.histogram(MetricIdentifier(NonEmptyList.of(name, "time"), tags), 10)

  // this metric does *not* calculate histogram of sizes of maps in the whole state,
  // but of those that are processed, so "hot" keys would be counted much more often.
  protected lazy val retrievedBucketsHistogram: util.metrics.Histogram =
    metricsProvider.histogram(MetricIdentifier(NonEmptyList.of(name, "retrievedBuckets"), tags), 10)

  protected def minimalResolutionMs: Long = 60000L

  protected def allowedOutOfOrderMs: Long = timeWindowLengthMillis

  protected val aggregator: Aggregator

  protected def timeWindowLengthMillis: Long

  protected def aggregateElementType: TypingResult

  protected val outputType: TypingResult = aggregator
    .computeOutputType(aggregateElementType)
    .valueOr(e => throw new IllegalArgumentException("Failed to compute output type: " + e))

  protected def aggregateTypeInformation: TypeInformation[AnyRef]

  @transient
  protected var bucketsState: BucketsState = _

  @transient
  protected var latestEvictionTimeForKey: ValueState[lang.Long] = _

  protected def handleNewElementAdded(
      value: ValueWithContext[KeyedValue[AnyRef, AnyRef]],
      timestamp: Long,
      timeService: TimerService,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    val start = System.nanoTime()
    addElementToState(value, timestamp, timeService, out)
    val finalVal = computeFinalValue(timestamp, bucketsState.keys)
    timeHistogram.update(System.nanoTime() - start)
    out.collect(ValueWithContext(finalVal, KeyEnricher.enrichWithKey(value.context, value.value)))

  }

  protected def addElementToState(
      value: ValueWithContext[KeyedValue[AnyRef, AnyRef]],
      timestamp: Long,
      timeService: TimerService,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    val bucketTs   = computeTimestampToStore(timestamp)
    val newElement = value.value.value.asInstanceOf[aggregator.Element]

    val currentAggregate = Option(bucketsState.get(bucketTs))
      .getOrElse(aggregator.createAccumulator())
      .asInstanceOf[aggregator.Aggregate]

    if (!aggregator.isNeutralForAccumulator(newElement, currentAggregate)) {
      val newAggregate = aggregator.add(newElement, currentAggregate)
      bucketsState.put(bucketTs, newAggregate)
    }

    doMoveEvictionTime(bucketTs + timeWindowLengthMillis, timeService)
    handleElementAddedToState(bucketTs, newElement, value.context, timeService, out)
  }

  // for extending classes purpose
  protected def handleElementAddedToState(
      newElementInStateTimestamp: Long,
      newElement: aggregator.Element,
      nkCtx: NkContext,
      timeService: TimerService,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {}

  protected def computeFinalValue(timestamp: Long, keys: java.util.List[lang.Long]): AnyRef = {
    val rangeStart = previousWindowEnd(timestamp) + 1
    var count      = 0

    val foldedState = keys
      .keysInRange(rangeStart, timestamp)
      .foldLeft(aggregator.createAccumulator()) { (acc, key) =>
        count += 1
        aggregator.merge(acc, bucketsState.get(key))
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
      bucketsState.clear()
      latestEvictionTimeForKey.update(null)
    } else if (latestEvictionTimeValue != null) {
      evictOldBuckets(timestamp)
      timerService.registerEventTimeTimer(latestEvictionTimeValue)
    }
  }

  private def computeTimestampToStore(timestamp: Long): Long = {
    (timestamp / minimalResolutionMs) * minimalResolutionMs
  }

  protected def evictOldBuckets(timestamp: Long): Unit = {

    /**
     * +1 gives an exclusive lower bound, matching Flink's half-open window range semantics [start, end):
     * the bucket at (previousWindowEnd) falls in the preceding window and must
     * not be included. The allowedOutOfOrderMs buffer retains buckets that may still receive late events.
     */
    val cutoff = previousWindowEnd(timestamp) + 1 - allowedOutOfOrderMs
    bucketsState.removeOlderThan(cutoff)
  }

  /**
   * Returns the end of the window preceding the current timer, i.e. timestamp shifted back by one full
   * window length. Used in tumbling-window logic to compute the aggregate for the window that just closed.
   */
  protected def previousWindowEnd(timestamp: Long): Long =
    timestamp - timeWindowLengthMillis

  protected def initState(): Unit = {
    val mapState = getRuntimeContext.getMapState(
      new MapStateDescriptor[lang.Long, AnyRef](
        "buckets",
        TypeInformation.of(classOf[lang.Long]),
        aggregateTypeInformation
      )
    )
    val keysState = getRuntimeContext.getState(
      new ValueStateDescriptor[java.util.List[lang.Long]](
        "keys",
        new ListTypeInfo(TypeInformation.of(classOf[lang.Long]))
      )
    )

    bucketsState = new BucketsState(mapState, keysState)
    initEvictionTimeState()
  }

  protected def initEvictionTimeState(): Unit = {
    latestEvictionTimeForKey = getRuntimeContext.getState[lang.Long](
      new ValueStateDescriptor[lang.Long]("timers", classOf[lang.Long])
    )
  }

  protected implicit class RichSortedList(val list: java.util.List[lang.Long]) {

    def keysInRange(from: lang.Long, to: lang.Long): View[lang.Long] = {
      val buf     = list.asScala
      val fromIdx = buf.search(from)
      val toIdx   = buf.search(to, fromIdx.insertionPoint, buf.length)
      val untilIdx = toIdx match {
        case Found(i)          => i + 1
        case InsertionPoint(i) => i
      }

      buf.view
        .slice(fromIdx.insertionPoint, untilIdx)
    }

    def hasElementsFrom(from: lang.Long): Boolean =
      list.asScala.search(from).insertionPoint < list.size()

    def last: lang.Long =
      list.get(list.size() - 1)
  }

  /**
   * Manages bucket data for MapState-based sliding window aggregators.
   *
   * Maintains a separate ValueState with bucket timestamps (keys) to avoid
   * expensive prefix scans over RocksDB MapState for key-only operations
   * like range checks, max computation, and eviction decisions.
   */
  class BucketsState(
      private val mapState: MapState[lang.Long, AnyRef],
      private val keysState: ValueState[java.util.List[lang.Long]]
  ) {

    /**
     * Keys are always ordered. We order them before saving them to the state
     */
    def keys: java.util.List[lang.Long] = keysState.value() match {
      case null => new java.util.ArrayList[lang.Long]()
      case v    => v
    }

    def get(key: lang.Long): AnyRef =
      mapState.get(key)

    def put(key: lang.Long, value: AnyRef): Unit = {
      mapState.put(key, value)
      val currentKeys = keys
      val index       = java.util.Collections.binarySearch(currentKeys, key)
      if (index < 0) {
        // We keep sorted list
        currentKeys.add(-(index + 1), key)
        keysState.update(currentKeys)
      }
    }

    def removeOlderThan(cutoff: lang.Long): Unit = {
      val (toRemove, toKeep) = keys.asScala.partition(_ < cutoff)
      if (toRemove.nonEmpty) {
        toRemove.foreach(mapState.remove)
        keysState.update(new java.util.ArrayList(toKeep.asJava))
      }
    }

    def clear(): Unit = {
      mapState.clear()
      keysState.clear()
    }

  }

}
