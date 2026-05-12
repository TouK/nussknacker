# AggregatorFunction MapState Migration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce MapState-based AggregatorFunction alongside existing ValueState implementation to reduce memory pressure from full SortedMap serialization on every event.

**Architecture:** Extract shared aggregation logic into `AggregatorFunctionBase` trait. Create parallel `AggregatorFunctionMixinWithMapState` with entry-by-entry MapState access and streaming fold. Wire via `useMapState` config flag in `AggregateWindowsConfig`, selectable through `slidingTransformer`.

**Tech Stack:** Scala 2.13, Apache Flink (KeyedProcessFunction, MapState API), ScalaTest + Flink MiniCluster for integration tests.

**Spec:** `docs/superpowers/specs/2026-05-08-aggregator-mapstate-design.md`

---

## File Structure

### New files

| File | Responsibility |
|---|---|
| `.../aggregate/AggregatorFunctionBase.scala` | Shared trait: metrics, constants, `computeTimestampToStore`, `outputType`, `handleElementAddedToState` hook |
| `.../aggregate/AggregatorFunctionMixinWithMapState.scala` | MapState-based state management: entry-by-entry access, streaming fold, timer/eviction |
| `.../aggregate/AggregatorFunctionWithMapState.scala` | Concrete class for sliding window (preserveContext=true) |
| `.../aggregate/EmitWhenEventLeftAggregatorFunctionWithMapState.scala` | Concrete class for sliding window with emit-on-leave (preserveContext=false) |

### Modified files

| File | Change |
|---|---|
| `.../aggregate/AggregatorFunction.scala` | `AggregatorFunctionMixin` extends `AggregatorFunctionBase` instead of `RichFunction`, remove duplicated members |
| `.../aggregate/AggregateWindowsConfig.scala` | Add `useMapState: Boolean = false` field |
| `.../aggregate/sampleTransformers.scala` | `SlidingAggregateTransformerV2`: object → class, receives config |
| `.../aggregate/transformers.scala` | `slidingTransformer`: add `useMapState` param, select implementation |
| `.../FlinkBaseUnboundedComponentProvider.scala` | Instantiate `SlidingAggregateTransformerV2` with config |

### Test file

| File | Responsibility |
|---|---|
| `.../aggregate/TransformersTest.scala` | Add MapState integration tests at end of existing file |

All paths relative to `engine/flink/components/base-unbounded/src/main/scala/pl/touk/nussknacker/engine/flink/util/transformer/` (source) or `engine/flink/components/base-tests/src/test/scala/pl/touk/nussknacker/engine/flink/util/transformer/` (tests).

---

## Task 1: Extract `AggregatorFunctionBase` trait

**Files:**
- Create: `engine/flink/components/base-unbounded/src/main/scala/pl/touk/nussknacker/engine/flink/util/transformer/aggregate/AggregatorFunctionBase.scala`

- [ ] **Step 1: Create `AggregatorFunctionBase.scala`**

```scala
package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import cats.data.NonEmptyList
import org.apache.flink.api.common.functions.{RichFunction, RuntimeContext}
import org.apache.flink.streaming.api.TimerService
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.{Context => NkContext, NodeId, ValueWithContext}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.util
import pl.touk.nussknacker.engine.util.metrics.{MetricIdentifier, MetricsProviderForScenario}
import pl.touk.nussknacker.engine.util.metrics.common.naming.nodeIdTag

trait AggregatorFunctionBase extends RichFunction {

  protected def aggregator: Aggregator

  protected def timeWindowLengthMillis: Long

  def nodeId: NodeId

  protected def aggregateElementType: TypingResult

  protected def convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext

  protected def name: String = "aggregator"

  protected def tags: Map[String, String] = Map(nodeIdTag -> nodeId.id)

  protected lazy val engineRuntimeContext: EngineRuntimeContext = convertToEngineRuntimeContext(getRuntimeContext)

  protected lazy val metricsProvider: MetricsProviderForScenario = engineRuntimeContext.metricsProvider

  protected lazy val timeHistogram: util.metrics.Histogram =
    metricsProvider.histogram(MetricIdentifier(NonEmptyList.of(name, "time"), tags), 10)

  protected lazy val retrievedBucketsHistogram: util.metrics.Histogram =
    metricsProvider.histogram(MetricIdentifier(NonEmptyList.of(name, "retrievedBuckets"), tags), 10)

  protected def minimalResolutionMs: Long = 60000L

  protected def allowedOutOfOrderMs: Long = timeWindowLengthMillis

  protected val outputType: TypingResult = aggregator
    .computeOutputType(aggregateElementType)
    .valueOr(e => throw new IllegalArgumentException("Failed to compute output type: " + e))

  protected def computeTimestampToStore(timestamp: Long): Long =
    (timestamp / minimalResolutionMs) * minimalResolutionMs

  protected def handleElementAddedToState(
      newElementInStateTimestamp: Long,
      newElement: aggregator.Element,
      nkCtx: NkContext,
      timeService: TimerService,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {}

}
```

- [ ] **Step 2: Verify it compiles**

Run: `sbt "flinkComponentsBaseUnbounded/compile"`

Expected: SUCCESS (new file, no dependencies on it yet)

---

## Task 2: Refactor `AggregatorFunctionMixin` to extend `AggregatorFunctionBase`

**Files:**
- Modify: `engine/flink/components/base-unbounded/src/main/scala/pl/touk/nussknacker/engine/flink/util/transformer/aggregate/AggregatorFunction.scala`

- [ ] **Step 1: Change `AggregatorFunctionMixin` to extend `AggregatorFunctionBase` and remove duplicated members**

In `AggregatorFunction.scala`, replace the trait declaration and remove members that now come from `AggregatorFunctionBase`:

Replace:
```scala
trait AggregatorFunctionMixin[MapT[K, V]] extends RichFunction { self: StateHolder[MapT[Long, AnyRef]] =>

  protected def convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext

  def nodeId: NodeId

  protected def name: String = "aggregator"

  protected def tags: Map[String, String] = Map(nodeIdTag -> nodeId.id)

  protected lazy val engineRuntimeContext: EngineRuntimeContext = convertToEngineRuntimeContext(getRuntimeContext)

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
```

With:
```scala
trait AggregatorFunctionMixin[MapT[K, V]] extends AggregatorFunctionBase { self: StateHolder[MapT[Long, AnyRef]] =>
```

Also remove `handleElementAddedToState` default implementation (now in Base):
```scala
  // for extending classes purpose
  protected def handleElementAddedToState(
      newElementInStateTimestamp: Long,
      newElement: aggregator.Element,
      nkCtx: NkContext,
      timeService: TimerService,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {}
```

And remove `computeTimestampToStore`:
```scala
  private def computeTimestampToStore(timestamp: Long): Long = {
    (timestamp / minimalResolutionMs) * minimalResolutionMs
  }
```

Note: `computeTimestampToStore` was `private` in the old code. In `AggregatorFunctionBase` it becomes `protected`. This is safe — no external code calls it, and the visibility is only widened.

Also remove unused imports that were only needed for the extracted members:
- `cats.data.NonEmptyList` (still needed if used elsewhere in the trait — check)
- `pl.touk.nussknacker.engine.util.metrics.{MetricIdentifier, MetricsProviderForScenario}`
- `pl.touk.nussknacker.engine.util.metrics.common.naming.nodeIdTag`

Keep the remaining state-specific methods: `handleNewElementAdded`, `addElementToState`, `computeFinalValue`, `computeFoldedAggregatedValue`, `computeNewState`, `stateForTimestampToSave`, `stateForTimestampToRead`, `stateForTimestampToReadUntilEnd`, `readStateOrInitial`, `stateDescriptor`.

- [ ] **Step 2: Verify compilation and existing tests pass**

Run: `sbt "flinkComponentsBaseUnbounded/compile"` then `sbt "flinkComponentsBaseTests/test"`

Expected: all existing tests pass — pure structural refactor, no behavior change.

---

## Task 3: Create `AggregatorFunctionMixinWithMapState` trait

**Files:**
- Create: `engine/flink/components/base-unbounded/src/main/scala/pl/touk/nussknacker/engine/flink/util/transformer/aggregate/AggregatorFunctionMixinWithMapState.scala`

- [ ] **Step 1: Create `AggregatorFunctionMixinWithMapState.scala`**

```scala
package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.api.common.functions.OpenContext
import org.apache.flink.api.common.state.{MapState, MapStateDescriptor, ValueState, ValueStateDescriptor}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.TimerService
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.{ValueWithContext, Context => NkContext}
import pl.touk.nussknacker.engine.flink.util.keyed.KeyEnricher
import pl.touk.nussknacker.engine.util.KeyedValue

import scala.jdk.CollectionConverters._

trait AggregatorFunctionMixinWithMapState extends AggregatorFunctionBase {

  protected def aggregateTypeInformation: TypeInformation[AnyRef]

  @transient
  protected var bucketState: MapState[java.lang.Long, AnyRef] = _

  @transient
  protected var latestEvictionTimeForKey: ValueState[java.lang.Long] = _

  protected def initMapState(): Unit = {
    bucketState = getRuntimeContext.getMapState(
      new MapStateDescriptor[java.lang.Long, AnyRef](
        "buckets",
        classOf[java.lang.Long],
        aggregateTypeInformation
      )
    )
    latestEvictionTimeForKey = getRuntimeContext.getState[java.lang.Long](
      new ValueStateDescriptor[java.lang.Long]("timers", classOf[java.lang.Long])
    )
  }

  protected def addElement(
      value: ValueWithContext[KeyedValue[AnyRef, AnyRef]],
      timestamp: Long,
      timeService: TimerService,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    val bucketTs   = computeTimestampToStore(timestamp)
    val newElement = value.value.value.asInstanceOf[aggregator.Element]

    val currentAggregate = Option(bucketState.get(bucketTs))
      .map(_.asInstanceOf[aggregator.Aggregate])
      .getOrElse(aggregator.createAccumulator().asInstanceOf[aggregator.Aggregate])

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
    addElement(value, timestamp, timeService, out)
    val finalVal = computeFinalValue(timestamp)
    timeHistogram.update(System.nanoTime() - start)
    out.collect(ValueWithContext(finalVal, KeyEnricher.enrichWithKey(value.context, value.value)))
  }

  protected def computeFinalValue(timestamp: Long): AnyRef = {
    val rangeStart = timestamp - timeWindowLengthMillis + 1
    var count      = 0

    val foldedState = bucketState.entries().asScala
      .filter { entry =>
        val ts = entry.getKey.longValue()
        ts >= rangeStart && ts <= timestamp
      }
      .toList
      .sortBy(_.getKey) // maintain timestamp order for order-dependent aggregators (e.g. ListAggregator)
      .foldLeft(aggregator.createAccumulator().asInstanceOf[aggregator.Aggregate]) { (acc, entry) =>
        count += 1
        aggregator.merge(acc, entry.getValue).asInstanceOf[aggregator.Aggregate]
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
    val keysToRemove = bucketState.keys().asScala
      .filter(_.longValue() < cutoff)
      .toList
    keysToRemove.foreach(bucketState.remove)
  }

  protected def evictStates(): Unit = {
    bucketState.clear()
    latestEvictionTimeForKey.update(null)
  }

}
```

- [ ] **Step 2: Verify it compiles**

Run: `sbt "flinkComponentsBaseUnbounded/compile"`

Expected: SUCCESS

---

## Task 4: Create `AggregatorFunctionWithMapState` class

**Files:**
- Create: `engine/flink/components/base-unbounded/src/main/scala/pl/touk/nussknacker/engine/flink/util/transformer/aggregate/AggregatorFunctionWithMapState.scala`

- [ ] **Step 1: Create `AggregatorFunctionWithMapState.scala`**

```scala
package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.api.common.functions.{OpenContext, RuntimeContext}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.{NodeId, ValueWithContext}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.util.KeyedValue

class AggregatorFunctionWithMapState(
    protected val aggregator: Aggregator,
    protected val timeWindowLengthMillis: Long,
    override val nodeId: NodeId,
    protected val aggregateElementType: TypingResult,
    protected val aggregateTypeInformation: TypeInformation[AnyRef],
    val convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext
) extends KeyedProcessFunction[AnyRef, ValueWithContext[KeyedValue[AnyRef, AnyRef]], ValueWithContext[AnyRef]]
    with AggregatorFunctionMixinWithMapState {

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
```

- [ ] **Step 2: Verify it compiles**

Run: `sbt "flinkComponentsBaseUnbounded/compile"`

Expected: SUCCESS

---

## Task 5: Create `EmitWhenEventLeftAggregatorFunctionWithMapState` class

**Files:**
- Create: `engine/flink/components/base-unbounded/src/main/scala/pl/touk/nussknacker/engine/flink/util/transformer/aggregate/EmitWhenEventLeftAggregatorFunctionWithMapState.scala`

- [ ] **Step 1: Create `EmitWhenEventLeftAggregatorFunctionWithMapState.scala`**

```scala
package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.api.common.functions.{OpenContext, RuntimeContext}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.TimerService
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.{Context => NkContext, NodeId, ValueWithContext}
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, EngineRuntimeContext}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.util.keyed.KeyEnricher
import pl.touk.nussknacker.engine.util.KeyedValue

import scala.jdk.CollectionConverters._

class EmitWhenEventLeftAggregatorFunctionWithMapState(
    protected val aggregator: Aggregator,
    protected val timeWindowLengthMillis: Long,
    override val nodeId: NodeId,
    protected val aggregateElementType: TypingResult,
    protected val aggregateTypeInformation: TypeInformation[AnyRef],
    val convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext
) extends KeyedProcessFunction[AnyRef, ValueWithContext[KeyedValue[AnyRef, AnyRef]], ValueWithContext[AnyRef]]
    with AggregatorFunctionMixinWithMapState {

  type FlinkCtx =
    KeyedProcessFunction[AnyRef, ValueWithContext[KeyedValue[AnyRef, AnyRef]], ValueWithContext[AnyRef]]#Context
  type FlinkOnTimerCtx =
    KeyedProcessFunction[AnyRef, ValueWithContext[KeyedValue[AnyRef, AnyRef]], ValueWithContext[AnyRef]]#OnTimerContext

  @transient
  private var contextIdGenerator: ContextIdGenerator = _

  override def open(openContext: OpenContext): Unit = {
    super.open(openContext)
    initMapState()
    contextIdGenerator = convertToEngineRuntimeContext(getRuntimeContext).contextIdGenerator(nodeId.id)
  }

  override def processElement(
      value: ValueWithContext[KeyedValue[AnyRef, AnyRef]],
      ctx: FlinkCtx,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    handleNewElementAdded(value, ctx.timestamp(), ctx.timerService(), out)
  }

  override protected def handleElementAddedToState(
      newElementInStateTimestamp: Long,
      newElement: aggregator.Element,
      nkCtx: NkContext,
      timerService: TimerService,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    timerService.registerEventTimeTimer(newElementInStateTimestamp + timeWindowLengthMillis)
  }

  override def onTimer(timestamp: Long, ctx: FlinkOnTimerCtx, out: Collector[ValueWithContext[AnyRef]]): Unit = {
    handleElementLeftSlide(timestamp, ctx, out)
    handleOnTimer(timestamp, ctx.timerService())
  }

  private def handleElementLeftSlide(
      timestamp: Long,
      ctx: FlinkOnTimerCtx,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    // Replicate original logic: check if entries exist in
    // [maxBucketTs - windowLength + 1, timestamp - windowLength]
    // This range represents entries that were visible when the last event was emitted
    // but are now outside the window boundary.
    val maxBucketTs = bucketState.keys().asScala
      .map(_.longValue())
      .maxOption
      .getOrElse(return)

    val rangeStart = maxBucketTs - timeWindowLengthMillis + 1
    val rangeEnd   = timestamp - timeWindowLengthMillis

    if (rangeEnd < rangeStart) return

    val hasLeavingEntries = bucketState.keys().asScala
      .exists(key => key >= rangeStart && key <= rangeEnd)

    if (hasLeavingEntries) {
      val finalVal = computeFinalValue(timestamp)
      out.collect(
        ValueWithContext(
          finalVal,
          KeyEnricher.enrichWithKey(NkContext(contextIdGenerator.nextContextId()), ctx.getCurrentKey)
        )
      )
    }
  }

}
```

- [ ] **Step 2: Verify it compiles**

Run: `sbt "flinkComponentsBaseUnbounded/compile"`

Expected: SUCCESS

---

## Task 6: Wire up transformers and create MapState component

**Files:**
- Modify: `engine/flink/components/base-unbounded/src/main/scala/pl/touk/nussknacker/engine/flink/util/transformer/aggregate/transformers.scala`
- Modify: `engine/flink/components/base-unbounded/src/main/scala/pl/touk/nussknacker/engine/flink/util/transformer/aggregate/sampleTransformers.scala`

No changes to `AggregateWindowsConfig` or `FlinkBaseUnboundedComponentProvider`.

- [ ] **Step 1: Add `useMapState` parameter to `slidingTransformer` in `transformers.scala`**

Change `slidingTransformer` signature from:

```scala
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
```

To:

```scala
  def slidingTransformer(
      groupBy: LazyParameter[AnyRef],
      groupByParameterName: ParameterName,
      aggregateBy: LazyParameter[AnyRef],
      aggregator: Aggregator,
      windowLength: Duration,
      variableName: String,
      emitWhenEventLeft: Boolean,
      explicitUidInStatefulOperators: FlinkCustomNodeContext => Boolean,
      useMapState: Boolean = false
  )(implicit nodeId: NodeId): ContextTransformation = {
```

Then change the aggregator function selection from:

```scala
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
```

To:

```scala
          val aggregatorFunction =
            (preserveContext, useMapState) match {
              case (true, false) =>
                new AggregatorFunction[SortedMap](
                  aggregator,
                  windowLength.toMillis,
                  nodeId,
                  aggregateBy.returnType,
                  typeInfos.storedTypeInfo,
                  fctx.convertToEngineRuntimeContext
                )
              case (true, true) =>
                new AggregatorFunctionWithMapState(
                  aggregator,
                  windowLength.toMillis,
                  nodeId,
                  aggregateBy.returnType,
                  typeInfos.storedTypeInfo,
                  fctx.convertToEngineRuntimeContext
                )
              case (false, false) =>
                new EmitWhenEventLeftAggregatorFunction[SortedMap](
                  aggregator,
                  windowLength.toMillis,
                  nodeId,
                  aggregateBy.returnType,
                  typeInfos.storedTypeInfo,
                  fctx.convertToEngineRuntimeContext
                )
              case (false, true) =>
                new EmitWhenEventLeftAggregatorFunctionWithMapState(
                  aggregator,
                  windowLength.toMillis,
                  nodeId,
                  aggregateBy.returnType,
                  typeInfos.storedTypeInfo,
                  fctx.convertToEngineRuntimeContext
                )
            }
```

- [ ] **Step 2: Create `SlidingAggregateTransformerWithMapState` in `sampleTransformers.scala`**

Add after `SlidingAggregateTransformerV2` (inside `object sampleTransformers`). This is a copy of `SlidingAggregateTransformerV2` that always passes `useMapState = true`:

```scala
  object SlidingAggregateTransformerWithMapState
      extends CustomStreamTransformer
      with UnboundedStreamComponent
      with ExplicitUidInOperatorsSupport
      with Serializable {

    private val groupByParameterName = ParameterName("groupBy")

    @MethodToInvoke(returnType = classOf[AnyRef])
    def execute(
        @ParamName("groupBy") groupBy: LazyParameter[AnyRef],
        @ParamName("aggregator")
        @AdditionalVariables(Array(new AdditionalVariable(name = "AGG", clazz = classOf[AggregateHelper])))
        @Editor(
          `type` = EditorType.FIXED_VALUES_EDITOR,
          possibleValues = Array(
            new LabeledExpression(label = "First", expression = "#AGG.first"),
            new LabeledExpression(label = "Last", expression = "#AGG.last"),
            new LabeledExpression(label = "Min", expression = "#AGG.min"),
            new LabeledExpression(label = "Max", expression = "#AGG.max"),
            new LabeledExpression(label = "Sum", expression = "#AGG.sum"),
            new LabeledExpression(label = "Average", expression = "#AGG.average"),
            new LabeledExpression(label = "CountWhen", expression = "#AGG.countWhen"),
            new LabeledExpression(label = "StddevPop", expression = "#AGG.stddevPop"),
            new LabeledExpression(label = "StddevSamp", expression = "#AGG.stddevSamp"),
            new LabeledExpression(label = "VarPop", expression = "#AGG.varPop"),
            new LabeledExpression(label = "VarSamp", expression = "#AGG.varSamp"),
            new LabeledExpression(label = "Median", expression = "#AGG.median"),
            new LabeledExpression(label = "List", expression = "#AGG.list"),
            new LabeledExpression(label = "Set", expression = "#AGG.set"),
            new LabeledExpression(label = "ApproximateSetCardinality", expression = "#AGG.approxCardinality")
          )
        )
        @Editor(`type` = EditorType.SPEL_EDITOR)
        aggregator: Aggregator,
        @ParamName("aggregateBy") aggregateBy: LazyParameter[AnyRef],
        @ParamName("windowLength") @DefaultValue("T(java.time.Duration).parse('PT1H')") length: java.time.Duration,
        @ParamName("emitWhenEventLeft") @DefaultValue("false") emitWhenEventLeft: Boolean,
        @OutputVariableName variableName: String
    )(implicit nodeId: NodeId): ContextTransformation = {
      val windowDuration = Duration(length.toMillis, TimeUnit.MILLISECONDS)
      transformers.slidingTransformer(
        groupBy,
        groupByParameterName,
        aggregateBy,
        aggregator,
        windowDuration,
        variableName,
        emitWhenEventLeft,
        explicitUidInStatefulOperators,
        useMapState = true
      )
    }

  }
```

- [ ] **Step 3: Verify compilation**

Run: `sbt "flinkComponentsBaseUnbounded/compile"`

Expected: SUCCESS

- [ ] **Step 4: Verify existing tests still pass**

Run: `sbt "flinkComponentsBaseTests/testOnly *TransformersTest"`

Expected: all existing tests pass — `SlidingAggregateTransformerV2` unchanged.

---

## Task 7: Write integration tests

**Files:**
- Modify: `engine/flink/components/base-tests/src/test/scala/pl/touk/nussknacker/engine/flink/util/transformer/aggregate/TransformersTest.scala`

Tests mirror the existing sliding aggregation tests from `TransformersTest`, but use the new `SlidingAggregateTransformerWithMapState` component registered as `"aggregate-sliding-mapstate"`. Same expected results — the MapState implementation must produce identical output to the ValueState one.

- [ ] **Step 1: Register the MapState component in `modelData` and add `slidingMapState` helper**

In `TransformersTest.scala`, modify `modelData` to also register the new component. Add the import:

```scala
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.sampleTransformers.SlidingAggregateTransformerWithMapState
```

In the `modelData` method, add the new component to the list:

```scala
  def modelData(
      collectingListener: => ResultsCollectingListener[Any],
      list: List[TestRecord] = List(),
      aggregateWindowsConfig: AggregateWindowsConfig = AggregateWindowsConfig.Default,
  ): LocalModelData = {
    val sourceComponent = SourceFactory.noParamUnboundedStreamFactory[TestRecord](
      EmitWatermarkAfterEachElementCollectionSource.create[TestRecord](list, _.timestamp, Duration.ofHours(1))
    )
    LocalModelData(
      ConfigFactory.empty(),
      ComponentDefinition("start", sourceComponent)
        :: ComponentDefinition("aggregate-sliding-mapstate", SlidingAggregateTransformerWithMapState)
        :: FlinkBaseUnboundedComponentProvider.create(
          DocsConfig.Default,
          aggregateWindowsConfig
        ) ::: FlinkBaseComponentProvider.Components
        ::: List(ComponentDefinition(eventTimeExtractionComponentName, CustomTimestampExtractingTransformation)),
      configCreator = new ConfigCreatorWithCollectingListener(collectingListener)
    )
  }
```

Add the `slidingMapState` helper method next to the existing `sliding` method:

```scala
  private def slidingMapState(
      aggregator: String,
      aggregateBy: String,
      emitWhenEventLeft: Boolean,
      afterAggregateExpression: String = "null"
  ) = {
    process(
      "aggregate-sliding-mapstate",
      aggregator,
      aggregateBy,
      "windowLength",
      Map("emitWhenEventLeft" -> emitWhenEventLeft.toString),
      afterAggregateExpression
    )
  }
```

- [ ] **Step 2: Add MapState sliding tests**

Add the following tests at the end of `TransformersTest`, before the closing `}`. These mirror existing sliding tests with identical expected results:

```scala
  // --- MapState-based sliding aggregation tests ---
  // These mirror existing sliding tests to verify MapState produces identical results.

  test("MapState: sum aggregate") {
    val id = "1"

    ResultsCollectingListenerHolder.withListener { collectingListener =>
      val model = modelData(
        collectingListener,
        List(TestRecordHours(id, 0, 1, "a"), TestRecordHours(id, 1, 2, "b"), TestRecordHours(id, 2, 5, "b"))
      )
      val testScenario = slidingMapState("#AGG.sum", "#input.eId", emitWhenEventLeft = false)

      runScenario(model, testScenario)
      val aggregateVariables = collectingListener.fragmentResultEndVariable[Number](id)
      aggregateVariables shouldBe List(1, 3, 7)
    }
  }

  test("MapState: sum aggregate with zeros") {
    val id = "1"

    ResultsCollectingListenerHolder.withListener { collectingListener =>
      val model = modelData(
        collectingListener,
        List(TestRecordHours(id, 0, 0, "a"), TestRecordHours(id, 1, 1, "b"), TestRecordHours(id, 2, 0, "b"))
      )
      val testScenario = slidingMapState("#AGG.sum", "#input.eId", emitWhenEventLeft = false)

      runScenario(model, testScenario)
      val aggregateVariables = collectingListener.fragmentResultEndVariable[Number](id)
      aggregateVariables shouldBe List(0, 1, 1)
    }
  }

  test("MapState: countWhen aggregate") {
    val id = "1"

    ResultsCollectingListenerHolder.withListener { collectingListener =>
      val model = modelData(
        collectingListener,
        List(TestRecordHours(id, 0, 1, "a"), TestRecordHours(id, 1, 2, "b"), TestRecordHours(id, 2, 5, "c"))
      )
      val testScenario =
        slidingMapState("#AGG.countWhen", """#input.str == "a" || #input.str == "b" """, emitWhenEventLeft = false)

      runScenario(model, testScenario)
      val aggregateVariables = collectingListener.fragmentResultEndVariable[Number](id)
      aggregateVariables shouldBe List(1, 2, 1)
    }
  }

  test("MapState: average aggregate") {
    val id = "1"

    ResultsCollectingListenerHolder.withListener { collectingListener =>
      val model = modelData(
        collectingListener,
        List(TestRecordHours(id, 0, 1, "a"), TestRecordHours(id, 1, 2, "b"), TestRecordHours(id, 2, 5, "b"))
      )
      val testScenario = slidingMapState("#AGG.average", "#input.eId", emitWhenEventLeft = false)

      runScenario(model, testScenario)
      val aggregateVariables = collectingListener.fragmentResultEndVariable[Number](id)
      aggregateVariables shouldBe List(1.0d, 1.5, 3.5)
    }
  }

  test("MapState: sum aggregate for out of order elements") {
    val id = "1"

    ResultsCollectingListenerHolder.withListener { collectingListener =>
      val model = modelData(
        collectingListener,
        List(
          TestRecordHours(id, 0, 1, "a"),
          TestRecordHours(id, 1, 2, "b"),
          TestRecordHours(id, 2, 5, "b"),
          TestRecordHours(id, 1, 1, "b")
        )
      )
      val testScenario = slidingMapState("#AGG.sum", "#input.eId", emitWhenEventLeft = false)

      runScenario(model, testScenario)
      val aggregateVariables = collectingListener.fragmentResultEndVariable[Number](id)
      aggregateVariables shouldBe List(1, 3, 7, 4)
    }
  }

  test("MapState: emit aggregate when event left the slide") {
    val id = "1"

    ResultsCollectingListenerHolder.withListener { collectingListener =>
      val model = modelData(
        collectingListener,
        List(
          TestRecordHours(id, 0, 1, ""),
          TestRecordHours(id, 1, 2, ""),
          TestRecordHours(id, 2, 5, "")
        )
      )
      val testScenario = slidingMapState("#AGG.sum", "#input.eId", emitWhenEventLeft = true)

      runScenario(model, testScenario)
      val aggregateVariables = collectingListener.fragmentResultEndVariable[Number](id)
      aggregateVariables shouldBe List(1, 3, 7, 5, 0)
    }
  }

  test("MapState: list aggregate") {
    val id = "1"

    ResultsCollectingListenerHolder.withListener { collectingListener =>
      val model = modelData(
        collectingListener,
        List(TestRecordHours(id, 0, 1, "a"), TestRecordHours(id, 1, 2, "b"), TestRecordHours(id, 2, 5, "b"))
      )
      val testScenario = slidingMapState("#AGG.list", "#input.eId", emitWhenEventLeft = false)

      runScenario(model, testScenario)
      val aggregateVariables = collectingListener.fragmentResultEndVariable[java.util.List[Any]](id)
      aggregateVariables shouldBe List(asList(1), asList(1, 2), asList(1, 2, 5))
    }
  }

  test("MapState: base aggregates test") {
    val id = "1"

    ResultsCollectingListenerHolder.withListener { collectingListener =>
      val model = modelData(
        collectingListener,
        List(
          TestRecordHours(id, 1, 2, "a"),
          TestRecordHours(id, 2, 1, "b")
        ),
      )

      val aggregates = List(
        ("sum", 3),
        ("first", 2),
        ("last", 1),
        ("max", 2),
        ("min", 1),
        ("list", util.Arrays.asList(2, 1)),
        ("approxCardinality", 2)
      )

      val testScenario = process(
        aggregates
          .map(_._1)
          .map(name =>
            AggregateData(
              "aggregate-sliding-mapstate",
              s"#AGG.$name",
              "#input.eId",
              "windowLength",
              Map("emitWhenEventLeft" -> "false"),
              name
            )
          ): _*
      )

      runScenario(model, testScenario)
      val lastResult = collectingListener.endVariablesForKey(id).last
      aggregates.foreach { case (name, expected) =>
        lastResult.variableTyped[AnyRef](s"fragmentResult$name").get shouldBe expected
      }
    }
  }
```

- [ ] **Step 2: Run the MapState tests**

Run: `sbt "flinkComponentsBaseTests/testOnly *TransformersTest -- -t \"MapState\""`

Expected: all 8 MapState tests PASS

- [ ] **Step 3: Run the full test suite to verify no regressions**

Run: `sbt "flinkComponentsBaseTests/testOnly *TransformersTest"`

Expected: all tests pass (old + new)

---

## Verification checklist

After all tasks complete:

- [ ] `sbt "flinkComponentsBaseUnbounded/compile"` — compiles
- [ ] `sbt "flinkComponentsBaseTests/testOnly *TransformersTest"` — all tests pass
- [ ] `sbt scalafmtCheckAll` — formatting OK (run `sbt scalafmtAll` to fix if needed)
