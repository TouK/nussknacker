package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.{Context => NkContext, _}
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.api.state.LatelyEvictableStateFunction

import scala.collection.immutable.TreeMap
import scala.concurrent.duration.Duration

object transformers {

  def slidingTransformer(keyBy: LazyParameter[String],
                         aggregateBy: LazyParameter[AnyRef],
                         aggregator: Aggregator,
                         windowLength: Duration,
                         variableName: String,
                        )(implicit nodeId: NodeId): ContextTransformation =
    ContextTransformation.definedBy(aggregator.toContextTransformation(variableName, aggregateBy))
      .implementedBy(
        FlinkCustomStreamTransformation((start: DataStream[NkContext], ctx: FlinkCustomNodeContext) => {
          start
            .map(new KeyWithValueMapper(ctx.lazyParameterHelper, keyBy, aggregateBy))
            .keyBy(_.value._1)
            .process(new AggregatorFunction(aggregator, windowLength.toMillis, ctx.nodeId))
        }))

  def tumblingTransformer(keyBy: LazyParameter[String],
                          aggregateBy: LazyParameter[AnyRef],
                          aggregator: Aggregator,
                          windowLength: Duration,
                          variableName: String,
                         )(implicit nodeId: NodeId): ContextTransformation =
    ContextTransformation.definedBy(aggregator.toContextTransformation(variableName, aggregateBy))
      .implementedBy(
        FlinkCustomStreamTransformation((start: DataStream[NkContext], ctx: FlinkCustomNodeContext) => {
          start
            .map(new KeyWithValueMapper(ctx.lazyParameterHelper, keyBy, aggregateBy))
            .keyBy(_.value._1)
            .window(TumblingProcessingTimeWindows.of(Time.milliseconds(windowLength.toMillis)))
            .aggregate(aggregator
              // this casting seems to be needed for Flink to infer TypeInformation correctly....
              .asInstanceOf[org.apache.flink.api.common.functions.AggregateFunction[ValueWithContext[(String, AnyRef)], AnyRef, ValueWithContext[Any]]])

        }))

}

//TODO: how to handle keyBy in more elegant way?
class KeyWithValueMapper(val lazyParameterHelper: FlinkLazyParameterFunctionHelper, key: LazyParameter[String], value: LazyParameter[AnyRef])
  extends RichMapFunction[NkContext, ValueWithContext[(String, AnyRef)]] with LazyParameterInterpreterFunction {

  private lazy val interpreter = lazyParameterInterpreter.syncInterpretationFunction(key.product(value)(lazyParameterInterpreter))

  override def map(ctx: NkContext): ValueWithContext[(String, AnyRef)] = ValueWithContext(interpreter(ctx), ctx)
}


//TODO: figure out if it's more convenient/faster to just use SlidingWindow with 60s slide and appropriate trigger?
class AggregatorFunction(aggregator: Aggregator, lengthInMillis: Long, nodeId: String)
  extends LatelyEvictableStateFunction[ValueWithContext[(String, AnyRef)], ValueWithContext[Any], TreeMap[Long, AnyRef]] {

  type FlinkCtx = KeyedProcessFunction[String, ValueWithContext[(String, AnyRef)], ValueWithContext[Any]]#Context

  //TODO make it configurable
  private val minimalResolutionMs = 60000L

  override def processElement(value: ValueWithContext[(String, AnyRef)], ctx: FlinkCtx, out: Collector[ValueWithContext[Any]]): Unit = {

    moveEvictionTime(lengthInMillis, ctx)

    val newElement = value.value._2.asInstanceOf[aggregator.Element]
    val newState: TreeMap[Long, aggregator.Aggregate] = computeNewState(newElement, ctx)

    state.update(newState)

    val finalVal: Any = computeFinalValue(newState)
    out.collect(ValueWithContext(finalVal, value.context))
  }

  private def computeFinalValue(newState: TreeMap[Long, aggregator.Aggregate]) = {
    val foldedState = newState.values.reduce(aggregator.merge)
    aggregator.getResult(foldedState)
  }

  private def computeNewState(newValue: aggregator.Element, ctx: FlinkCtx): TreeMap[Long, aggregator.Aggregate] = {

    val current: TreeMap[Long, aggregator.Aggregate] = currentState(ctx)

    val timestampToUse = computeTimestampToStore(ctx)

    val currentAggregate = current.getOrElse(timestampToUse, aggregator.createAccumulator())
    val newAggregate = aggregator.add(newValue, currentAggregate).asInstanceOf[aggregator.Aggregate]

    current.updated(timestampToUse, newAggregate)
  }

  private def computeTimestampToStore(ctx: FlinkCtx): Long = {
    (ctx.timestamp() / minimalResolutionMs) * minimalResolutionMs
  }

  private def currentState(ctx: FlinkCtx): TreeMap[Long, aggregator.Aggregate] = {
    val currentState = Option(state.value().asInstanceOf[TreeMap[Long, aggregator.Aggregate]]).getOrElse(TreeMap[Long, aggregator.Aggregate]()(Ordering.Long))
    currentState.from(ctx.timestamp() - lengthInMillis)
  }

  override protected def stateDescriptor: ValueStateDescriptor[TreeMap[Long, AnyRef]]
  = new ValueStateDescriptor[TreeMap[Long, AnyRef]]("state", classOf[TreeMap[Long, AnyRef]])
}



