# AggregatorFunction MapState Migration — Design Spec

## Problem

`AggregatorFunction` stores a `SortedMap[Long, AnyRef]` (partial aggregates per minute bucket) inside
a `ValueState`. Every event causes full deserialization and re-serialization of the entire map.
On machines with limited RAM this creates memory pressure (2-3 in-memory copies of the map during
processing due to immutable SortedMap operations), leading to swap usage, HDD I/O contention, and
iowait-driven crashes.

## Goal

Introduce a parallel `MapState`-based implementation of `AggregatorFunction` that:

- Writes only the changed bucket per event (O(1) instead of O(N))
- Avoids deserializing the entire map into Java heap
- Computes fold via streaming iterator (peak heap: 1 entry instead of N)
- Coexists alongside the old `ValueState` implementation
- Shares business logic with the old implementation via extracted base trait

## Scope

### In scope

- `AggregatorFunctionWithMapState` — new, for `slidingTransformer` with `preserveContext = true`
- `EmitWhenEventLeftAggregatorFunctionWithMapState` — new, for `slidingTransformer` with `preserveContext = false`
- `AggregatorFunctionBase` — new shared trait extracted from `AggregatorFunctionMixin`
- `AggregatorFunctionMixin` — refactored to extend `AggregatorFunctionBase`
- `transformers.slidingTransformer` — `useMapState: Boolean = false` parameter to select implementation
- `SlidingAggregateTransformerWithMapState` — new Nussknacker component (separate from `SlidingAggregateTransformerV2`)

### Out of scope (phase 2)

- `EmitExtraWindowWhenNoDataTumblingAggregatorFunction` — own state management, separate migration
- `FullOuterJoinAggregatorFunction` — join transformer, separate use case
- `SingleSideJoinAggregatorFunction` — `CoProcessFunction`, different base hierarchy
- State migration from old to new — users consciously switch to the new component, accepting state loss

## Architecture

### Trait hierarchy

```
AggregatorFunctionBase                         [NEW] shared: metrics, constants, computeTimestampToStore,
|                                                    outputType, handleElementAddedToState hook
|
+-- AggregatorFunctionMixin[MapT]              [REFACTOR] extends Base instead of RichFunction,
|   |                                                     keeps FlinkRangeMap + StateHolder logic
|   +-- AggregatorFunction[MapT]               [UNCHANGED]
|   +-- EmitWhenEventLeftAggregatorFunction    [UNCHANGED]
|   +-- FullOuterJoinAggregatorFunction        [UNCHANGED]
|   +-- SingleSideJoinAggregatorFunction       [UNCHANGED]
|   +-- EmitExtraWindowWhenNoDataTumblingAggregatorFunction  [UNCHANGED]
|
+-- AggregatorFunctionMixinWithMapState            [NEW] MapState[Long, AnyRef], entry-by-entry access,
    |                                                streaming fold, partial eviction, timer management
    +-- AggregatorFunctionWithMapState             [NEW]
    +-- EmitWhenEventLeftAggregatorFunctionWithMapState  [NEW]
```

### What moves into `AggregatorFunctionBase`

Extracted from current `AggregatorFunctionMixin` (no behavior change):

| Element | Description |
|---|---|
| `name`, `tags` | Metric naming |
| `metricsProvider`, `timeHistogram`, `retrievedBucketsHistogram` | Metric instances |
| `minimalResolutionMs`, `allowedOutOfOrderMs` | Window constants |
| `outputType` | Derived from `aggregator.computeOutputType` |
| `computeTimestampToStore(timestamp)` | Pure function: `(timestamp / minimalResolutionMs) * minimalResolutionMs` |
| `handleElementAddedToState(...)` | Hook for subclasses, default no-op |

### What stays in `AggregatorFunctionMixin[MapT]`

State-specific logic using `FlinkRangeMap` and `StateHolder`:

- `readStateOrInitial()`, `addElementToState()`, `handleNewElementAdded()`
- `computeFinalValue()`, `computeFoldedAggregatedValue()`
- `computeNewState()`, `stateForTimestampToSave/Read/ReadUntilEnd()`
- `stateDescriptor` (ValueStateDescriptor)

## AggregatorFunctionMixinWithMapState — detailed design

### State initialization

```scala
@transient protected var bucketState: MapState[java.lang.Long, AnyRef]
@transient protected var latestEvictionTimeForKey: ValueState[java.lang.Long]
```

State names:
- `"buckets"` — MapState for per-bucket aggregates (different from old `"state"`)
- `"timers"` — ValueState for eviction timer (same name and semantics as old code)

### Hot path: `addElement` + `handleNewElementAdded`

Two levels of API:

`addElement(value, timestamp, timeService, out)`:
1. `bucketTs = computeTimestampToStore(timestamp)` — from AggregatorFunctionBase
2. `current = bucketState.get(bucketTs)` — 1 RocksDB read
3. If not neutral: `bucketState.put(bucketTs, aggregator.add(newElement, current))` — 1 RocksDB write
4. `doMoveEvictionTime(bucketTs + timeWindowLengthMillis, timeService)`
5. `handleElementAddedToState(...)` — hook

`handleNewElementAdded(value, timestamp, timeService, out)`:
1. `addElement(...)`
2. `finalVal = computeFinalValue(timestamp)` — streaming fold
3. `out.collect(ValueWithContext(finalVal, ...))`

Separation needed because:
- `AggregatorFunction` uses `handleNewElementAdded` (add + fold + emit)
- `FullOuterJoinAggregatorFunction` (phase 2) would use `addElement` only, then fold and modify result

### Streaming fold: `computeFinalValue(timestamp)`

```
rangeStart = timestamp - timeWindowLengthMillis + 1
acc = aggregator.createAccumulator()
count = 0

for entry in bucketState.entries():        // prefix scan, sequential I/O
  if entry.key >= rangeStart && entry.key <= timestamp:
    acc = aggregator.merge(acc, entry.value)
    count += 1

retrievedBucketsHistogram.update(count)
return aggregator.alignToExpectedType(aggregator.getResult(acc), outputType)
```

Key property: only `acc` + 1 entry in heap at a time (vs N entries with ValueState).

### Eviction strategy

No per-event trimming (would require iterating all keys). Instead:

| Trigger | Action |
|---|---|
| Per event | No trimming. `computeFinalValue` filters by range. |
| Timer, key inactive | `bucketState.clear()` — full eviction |
| Timer, key active | `evictOldBuckets(timestamp)` — partial cleanup, then re-register timer |

`evictOldBuckets(currentWatermark)`:

```
cutoff = currentWatermark - timeWindowLengthMillis + 1 - allowedOutOfOrderMs

keysToRemove = []
for key in bucketState.keys():          // keys only, no value deserialization
  if key < cutoff:
    keysToRemove.add(key)

for key in keysToRemove:                // separate loop: MapState forbids
  bucketState.remove(key)               // remove during iteration
```

### Timer management

Duplicated from `LatelyEvictableStateFunctionMixin` (~20 lines, stable code):

- `doMoveEvictionTime(time, timeService)` — tracks max eviction time, registers timer on first event
- `handleOnTimer(timestamp, timerService)` — if timer matches latest: full evict; else: partial evict + re-register
- `evictStates()` — `bucketState.clear()` + `latestEvictionTimeForKey.update(null)`

Not extracted to shared trait because `LatelyEvictableStateFunctionMixin` is used by unrelated
components (`UnionWithMemoTransformer`, `TransformStateTransformer`) and the blast radius of that
refactor is not justified.

## Concrete classes

### `AggregatorFunctionWithMapState`

```
extends KeyedProcessFunction[AnyRef, ValueWithContext[KeyedValue[AnyRef, AnyRef]], ValueWithContext[AnyRef]]
with AggregatorFunctionMixinWithMapState

Constructor: same parameters as AggregatorFunction (aggregator, timeWindowLengthMillis, nodeId,
             aggregateElementType, aggregateTypeInformation, convertToEngineRuntimeContext)

processElement -> handleNewElementAdded(...)
onTimer        -> handleOnTimer(...)
```

### `EmitWhenEventLeftAggregatorFunctionWithMapState`

```
extends KeyedProcessFunction[AnyRef, ValueWithContext[KeyedValue[AnyRef, AnyRef]], ValueWithContext[AnyRef]]
with AggregatorFunctionMixinWithMapState

Additional field: contextIdGenerator (initialized in open())

handleElementAddedToState -> registers timer at bucketTs + timeWindowLengthMillis
onTimer -> handleElementLeftSlide(...) then handleOnTimer(...)
```

`handleElementLeftSlide`: single pass over `bucketState.entries()` to find max bucket timestamp
and check for entries leaving the window. If entries are leaving, emits fold result.

## Transformer wiring

### `transformers.slidingTransformer`

New parameter `useMapState: Boolean = false`:

```scala
def slidingTransformer(
  ...,
  useMapState: Boolean = false
)(implicit nodeId: NodeId)
```

Selection logic:

```scala
val aggregatorFunction = (preserveContext, useMapState) match {
  case (true, false)  => new AggregatorFunction[SortedMap](...)
  case (true, true)   => new AggregatorFunctionWithMapState(...)
  case (false, false) => new EmitWhenEventLeftAggregatorFunction[SortedMap](...)
  case (false, true)  => new EmitWhenEventLeftAggregatorFunctionWithMapState(...)
}
```

Rest of pipeline unchanged — stream is already keyed via `groupByWithValue`.

### Separate component: `SlidingAggregateTransformerWithMapState`

Instead of a config flag on the existing component, a new standalone Nussknacker component
is created in `sampleTransformers.scala`:

- `SlidingAggregateTransformerWithMapState` — an `object` mirroring `SlidingAggregateTransformerV2`,
  but always passes `useMapState = true` to `slidingTransformer`
- Same parameters as `SlidingAggregateTransformerV2` (no `useMapState` exposed to user)
- `SlidingAggregateTransformerV2` stays **unchanged**

Users choose the implementation by selecting the component, not by setting a flag.
No changes to `AggregateWindowsConfig` or `FlinkBaseUnboundedComponentProvider`.

## Performance characteristics

Assuming N = number of buckets in window (e.g., 60 for 1h with 1min resolution).

| Metric | ValueState (old) | MapState (new) |
|---|---|---|
| RocksDB writes per event | 1 large (N entries serialized) | 1 small (1 entry) |
| RocksDB reads per event | 1 large (N entries deserialized) | 1 small + N small (fold via prefix scan) |
| Serialization ops per event | ~2N | ~N+2 |
| Peak Java heap per event | 2-3 copies of full SortedMap (N entries each) | accumulator + 1 entry |
| Per-event trimming | In-memory O(log N) | None (deferred to timer) |
| Write amplification (RocksDB) | High (large values cause more compaction) | Low (small values) |
| Eviction on timer | `state.clear()` | `bucketState.clear()` or partial `remove()` per old key |

## State compatibility

| State name | Old implementation | New implementation |
|---|---|---|
| `"state"` | `ValueState[SortedMap[Long, AnyRef]]` | Not used |
| `"buckets"` | Not used | `MapState[java.lang.Long, AnyRef]` |
| `"timers"` | `ValueState[java.lang.Long]` | `ValueState[java.lang.Long]` (same) |

No state migration. Users consciously switch to new component, accepting state loss
(state rebuilds within `timeWindowLengthMillis`).

## Risks

1. **Fold performance**: streaming fold via MapState prefix scan may be slower than in-memory fold
   for small N. Monitor `retrievedBucketsHistogram` and `timeHistogram` after deployment.
2. **Temporary state bloat**: without per-event trimming, MapState may hold extra old buckets
   until timer-based eviction. Mitigated by partial eviction in `handleOnTimer`.
3. **Refactor risk**: changing `AggregatorFunctionMixin extends RichFunction` to
   `extends AggregatorFunctionBase` — purely structural, no behavior change, but requires
   test verification.
