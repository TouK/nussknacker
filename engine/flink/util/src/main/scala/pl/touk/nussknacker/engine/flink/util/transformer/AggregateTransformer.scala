package pl.touk.nussknacker.engine.flink.util.transformer

import java.util

import com.codahale.metrics
import com.codahale.metrics.ExponentiallyDecayingReservoir
import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.configuration.Configuration
import org.apache.flink.dropwizard.metrics.DropwizardHistogramWrapper
import org.apache.flink.metrics.Histogram
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.{Context => NkContext}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass}
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.api.state.EvictableStateFunction

import scala.collection.JavaConverters._
import scala.collection.immutable.TreeMap
import scala.concurrent.duration.Duration

object AggregateTransformer extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[AnyRef])
  def execute(@ParamName("keyBy") keyBy: LazyParameter[String],
              @ParamName("length") length: String,
              @ParamName("aggregateBy") aggregateBy: LazyParameter[AnyRef])
  = FlinkCustomStreamTransformation((start: DataStream[Context], ctx: FlinkCustomNodeContext) => {
    val lengthInMillis = Duration(length).toMillis

    start
      .map(ctx.lazyParameterHelper.lazyMapFunction(keyBy))
      .keyBy(_.value)
      .process(new AggregatorFunction[AnyRef](ctx.lazyParameterHelper, aggregateBy, lengthInMillis, ctx.nodeId))
  }, TypedClass(classOf[java.util.List[_]], List(aggregateBy.returnType)))

}


//TODO: other aggregations, make consistent with TImestampedEvictableState
class AggregatorFunction[T <: AnyRef](val lazyParameterHelper: FlinkLazyParameterFunctionHelper, aggregateBy: LazyParameter[T], lengthInMillis: Long, nodeId: String)
  extends EvictableStateFunction[ValueWithContext[String], ValueWithContext[Any], TreeMap[Long, AnyRef]] with LazyParameterInterpreterFunction {

  type AggregationType = List[AnyRef]

  type Ctx = KeyedProcessFunction[String, ValueWithContext[String], ValueWithContext[Any]]#Context

  private val minimalResolutionMs = 60000L

  private var histogramTotal: Histogram = _

  private lazy val aggregateByInterpreter = lazyParameterInterpreter.syncInterpretationFunction[T](aggregateBy)

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    histogramTotal = getRuntimeContext
      .getMetricGroup
      .addGroup("sizeTotal")
      .histogram(nodeId, new DropwizardHistogramWrapper(new metrics.Histogram(new ExponentiallyDecayingReservoir)))
  }

  override def stateDescriptor: ValueStateDescriptor[TreeMap[Long, AnyRef]]
  = new ValueStateDescriptor[TreeMap[Long, AggregationType]]("state", classOf[TreeMap[Long, AggregationType]]).asInstanceOf[ValueStateDescriptor[TreeMap[Long, AnyRef]]]

  override def processElement(ir: ValueWithContext[String], ctx: Ctx, out: Collector[ValueWithContext[Any]]): Unit = {

    moveEvictionTime(lengthInMillis, ctx)

    val newState: TreeMap[Long, AggregationType] = computeNewState(ir.context, ctx)

    histogramTotal.update(newState.size)
    state.update(newState.asInstanceOf[TreeMap[Long, AnyRef]])

    val finalVal: Any = computeFinalValue(newState)

    out.collect(ValueWithContext(finalVal, ir.context))

  }

  private def computeFinalValue(newState: TreeMap[Long, AggregationType]) = {
    val foldedState = aggregator.aggregateFunction(newState.values)
    aggregator.finalAggregation(foldedState)
  }

  private def computeNewState(ctx: NkContext, functionCtx: Ctx) = {
    val newValue = aggregateByInterpreter(ctx)

    val current: TreeMap[Long, AggregationType] = currentState(functionCtx)

    val timestamp = computeTimestampToStore(functionCtx)

    val valueForTimestamp = current.getOrElse(timestamp, aggregator.zero)

    current.updated(timestamp, aggregator.add(valueForTimestamp, newValue))
  }

  private def computeTimestampToStore(ctx: Ctx) = {
    (ctx.timestamp() / minimalResolutionMs) * minimalResolutionMs
  }

  private def currentState(ctx: Ctx): TreeMap[Long, AggregationType] = {
    val currentState = Option(state.value().asInstanceOf[TreeMap[Long, AggregationType]]).getOrElse(TreeMap[Long, AggregationType]()(Ordering.Long))
    currentState.from(ctx.timestamp() - lengthInMillis)
  }

}

object aggregator {
  val zero = List()

  def add(list: List[AnyRef], newElement: AnyRef): List[AnyRef] = list :+ newElement

  def aggregateFunction(list: Iterable[List[AnyRef]]): List[AnyRef] = list.flatten.toList

  def finalAggregation(list: List[AnyRef]) = new util.ArrayList[Any](list.asJava)
}


