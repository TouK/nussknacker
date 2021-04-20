package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import java.util.concurrent.TimeUnit

import cats.data.NonEmptyList
import com.codahale.metrics.{Histogram, SlidingTimeWindowReservoir}
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala._
import org.apache.flink.dropwizard.metrics.DropwizardHistogramWrapper
import org.apache.flink.metrics
import org.apache.flink.streaming.api.TimerService
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.{ValueWithContext, Context => NkContext}
import pl.touk.nussknacker.engine.flink.api.state.{LatelyEvictableStateFunction, StateHolder}
import pl.touk.nussknacker.engine.flink.util.keyed.StringKeyedValue
import pl.touk.nussknacker.engine.flink.util.metrics.MetricUtils
import pl.touk.nussknacker.engine.flink.util.orderedmap.FlinkRangeMap
import pl.touk.nussknacker.engine.flink.util.orderedmap.FlinkRangeMap._

import scala.language.higherKinds

// This is the real SlidingWindow with slide = 1min - moving with time for each key. It reduce on each emit and store
// partial aggregations for each minute, instead of storing aggregates for each slide. This implementation doesn't emit event
// when some element left the slide. If you want to handle this situation, you should use `EmitWhenEventLeftAggregatorFunction`
// Flinks SlidingWindows are something that is often called HoppingWindow. They consume a lot of memory because each element
// is stored in multiple windows with different offsets.
// NOTE: it would be much cleaner if we evaluated aggregateBy here. However, FLINK-10250 prevents us from doing this and we *have* to compute it before
class AggregatorFunction[MapT[K,V]](protected val aggregator: Aggregator, protected val timeWindowLengthMillis: Long,
                                    override val nodeId: NodeId, protected val aggregateElementType: TypingResult)
                                   (implicit override val rangeMap: FlinkRangeMap[MapT])
  extends LatelyEvictableStateFunction[ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef], MapT[Long, AnyRef]]
  with AggregatorFunctionMixin[MapT] {

  type FlinkCtx = KeyedProcessFunction[String, ValueWithContext[StringKeyedValue[AnyRef]], ValueWithContext[AnyRef]]#Context

  override def processElement(value: ValueWithContext[StringKeyedValue[AnyRef]], ctx: FlinkCtx, out: Collector[ValueWithContext[AnyRef]]): Unit = {
    handleNewElementAdded(value, ctx.timestamp(), ctx.timerService(), out)
  }

}

trait AggregatorFunctionMixin[MapT[K,V]] { self: StateHolder[MapT[Long, AnyRef]] =>

  def getRuntimeContext: RuntimeContext

  def nodeId: NodeId

  protected def name: String = "aggregator"

  protected def tags: Map[String, String] = Map("nodeId" -> nodeId.id)

  protected def newHistogram()
    = new DropwizardHistogramWrapper(new Histogram(new SlidingTimeWindowReservoir(10, TimeUnit.SECONDS)))

  protected lazy val metricUtils = new MetricUtils(getRuntimeContext)

  protected lazy val timeHistogram: metrics.Histogram
    = metricUtils.histogram(NonEmptyList.of(name, "time"), tags, newHistogram())

  //this metric does *not* calculate histogram of sizes of maps in the whole state,
  //but of those that are processed, so "hot" keys would be counted much more often. 
  protected lazy val retrievedBucketsHistogram: metrics.Histogram
    = metricUtils.histogram(NonEmptyList.of(name, "retrievedBuckets"), tags, newHistogram())

  protected def minimalResolutionMs: Long = 60000L

  protected def allowedOutOfOrderMs: Long = timeWindowLengthMillis

  protected val aggregator: Aggregator

  protected def timeWindowLengthMillis: Long

  protected def aggregateElementType: TypingResult

  protected val outputType: TypingResult = aggregator.computeOutputType(aggregateElementType).valueOr(e =>
    throw new IllegalArgumentException("Failed to compute output type: " + e))

  protected implicit def rangeMap: FlinkRangeMap[MapT]

  protected def handleNewElementAdded(value: ValueWithContext[StringKeyedValue[AnyRef]],
                                      timestamp: Long, timeService: TimerService,
                                      out: Collector[ValueWithContext[AnyRef]]): Unit = {
    val start = System.nanoTime()
    val newState = addElementToState(value, timestamp, timeService, out)
    val finalVal = computeFinalValue(newState, timestamp)
    timeHistogram.update(System.nanoTime() - start)
    out.collect(ValueWithContext(finalVal, value.context))
  }

  protected def addElementToState(value: ValueWithContext[StringKeyedValue[AnyRef]],
                                  timestamp: Long, timeService: TimerService,
                                  out: Collector[ValueWithContext[AnyRef]]): MapT[Long, aggregator.Aggregate] = {
    val newElementInStateTimestamp = computeTimestampToStore(timestamp)
    val newElement = value.value.value.asInstanceOf[aggregator.Element]
    val newState = computeNewState(newElementInStateTimestamp, newElement)

    updateState(newState.asInstanceOf[MapT[Long, AnyRef]], newElementInStateTimestamp + timeWindowLengthMillis, timeService)
    handleElementAddedToState(newElementInStateTimestamp, newElement, value.context, timeService, out)
    retrievedBucketsHistogram.update(newState.toScalaMapRO.size)
    newState
  }

  // for extending classes purpose
  protected def handleElementAddedToState(newElementInStateTimestamp: Long, newElement: aggregator.Element, nkCtx: NkContext,
                                          timeService: TimerService, out: Collector[ValueWithContext[AnyRef]]): Unit = {
  }

  protected def computeFinalValue(newState: MapT[Long, aggregator.Aggregate], timestamp: Long): AnyRef = {
    aggregator.alignToExpectedType(computeFoldedAggregatedValue(newState, timestamp), outputType)
  }

  protected def computeFoldedAggregatedValue(state: MapT[Long, aggregator.Aggregate], timestamp: Long): AnyRef = {
    val newStateTruncatedToTimestamp = stateForTimestampToRead(state, timestamp)
    val foldedState = if (newStateTruncatedToTimestamp.toScalaMapRO.isEmpty) {
      aggregator.createAccumulator()
    } else {
      newStateTruncatedToTimestamp.toScalaMapRO.values.reduce(aggregator.merge)
    }
    aggregator.getResult(foldedState)
  }

  private def computeNewState(newElementInStateTimestamp: Long, newValue: aggregator.Element): MapT[Long, aggregator.Aggregate] = {
    val current: MapT[Long, aggregator.Aggregate] = stateForTimestampToSave(readStateOrInitial(), newElementInStateTimestamp)

    // We do not create aggregate and add to it neutral element to avoid unnecessary buckets in our state
    if (aggregator.isNeutralForAccumulator(newValue)) {
      current
    } else {
      val currentAggregate = current.toScalaMapRO.getOrElse(newElementInStateTimestamp, aggregator.createAccumulator())
      val newAggregate = aggregator.add(newValue, currentAggregate).asInstanceOf[aggregator.Aggregate]

      current.updated(newElementInStateTimestamp, newAggregate)
    }
  }

  private def computeTimestampToStore(timestamp: Long): Long = {
    (timestamp / minimalResolutionMs) * minimalResolutionMs
  }

  protected def stateForTimestampToSave[T](stateValue: MapT[Long, T], timestamp: Long): MapT[Long, T] = {
    stateValue.from(timestamp - timeWindowLengthMillis + 1 - allowedOutOfOrderMs) // we must have exclusive range like Flink's sliding/tumbling have
  }

  protected def stateForTimestampToRead[T](stateValue: MapT[Long, T], timestamp: Long): MapT[Long, T] = {
    stateForTimestampToReadUntilEnd(stateValue, timestamp).toRO(timestamp) // we must have exclusive range like Flink's sliding/tumbling have
  }

  protected def stateForTimestampToReadUntilEnd[T](stateValue: MapT[Long, T], timestamp: Long): MapT[Long, T] = {
    stateValue.fromRO(timestamp - timeWindowLengthMillis + 1)
  }

  protected def readStateOrInitial(): MapT[Long, aggregator.Aggregate] =
    Option(readState().asInstanceOf[MapT[Long, aggregator.Aggregate]]).getOrElse(rangeMap.empty[Long, aggregator.Aggregate])

  override protected def stateDescriptor: ValueStateDescriptor[MapT[Long, AnyRef]] =
    new ValueStateDescriptor[MapT[Long, AnyRef]]("state",
      rangeMap.typeInformation[Long, AnyRef](implicitly[TypeInformation[Long]], aggregateTypeInformation))

  // TODO pass type information based on validatedStoredType for fast (de)serialization
  protected def aggregateTypeInformation: TypeInformation[AnyRef] = {
    implicitly[TypeInformation[AnyRef]]
  }

}
