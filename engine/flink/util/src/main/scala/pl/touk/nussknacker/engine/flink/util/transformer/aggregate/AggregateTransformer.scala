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

object AggregateTransformer extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[AnyRef])
  def execute(@ParamName("keyBy") keyBy: LazyParameter[String],
              @ParamName("length") length: String,
              @ParamName("aggregator") aggregator: Aggregator,
              @ParamName("aggregateBy") aggregateBy: LazyParameter[AnyRef],
              @OutputVariableName variableName: String)(implicit nodeId: NodeId): ContextTransformation = ContextTransformation
    .definedBy(aggregator.toContextTransformation(variableName, aggregateBy))
    .implementedBy(
      FlinkCustomStreamTransformation((start: DataStream[NkContext], ctx: FlinkCustomNodeContext) => {
        val windowMillis = Duration(length).toMillis
        start
          .map(new KeyWithValueMapper(ctx.lazyParameterHelper, keyBy, aggregateBy))
          .keyBy(_.value._1)
          .process(new AggregatorFunction[AnyRef](aggregator, windowMillis, ctx.nodeId))
      }))
}


object AggregateTumblingTransformer extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[AnyRef])
  def execute(@ParamName("keyBy") keyBy: LazyParameter[String],
              @ParamName("lengthInSe") length: String,
              @ParamName("aggregator") aggregator: Aggregator,
              @ParamName("aggregateBy") aggregateBy: LazyParameter[AnyRef],
              @OutputVariableName variableName: String)(implicit nodeId: NodeId): ContextTransformation = ContextTransformation
    .definedBy(aggregator.toContextTransformation(variableName, aggregateBy))
    .implementedBy(
      FlinkCustomStreamTransformation((start: DataStream[NkContext], ctx: FlinkCustomNodeContext) => {
        val windowMillis = Duration(length).toMillis

        start
          .map(new KeyWithValueMapper(ctx.lazyParameterHelper, keyBy, aggregateBy))
          .keyBy(_.value)
          .window(TumblingProcessingTimeWindows.of(Time.milliseconds(windowMillis)))
          .aggregate(aggregator
            // this casting seems to be needed for Flink to infer TypeInformation correctly....
            .asInstanceOf[org.apache.flink.api.common.functions.AggregateFunction[ValueWithContext[(String, AnyRef)], AnyRef, ValueWithContext[Any]]])
      }))

}

//TODO: how to handle keyBy in more elegant way?
class KeyWithValueMapper(val lazyParameterHelper: FlinkLazyParameterFunctionHelper, key: LazyParameter[String], value: LazyParameter[AnyRef])
  extends RichMapFunction[NkContext, ValueWithContext[(String, AnyRef)]] with LazyParameterInterpreterFunction {

  private lazy val keyInterpreter = lazyParameterInterpreter.syncInterpretationFunction(key)

  private lazy val valueInterpreter = lazyParameterInterpreter.syncInterpretationFunction(value)

  override def map(value: NkContext): ValueWithContext[(String, AnyRef)] = {
    ValueWithContext((keyInterpreter(value), valueInterpreter(value)), value)
  }
}


//TODO: figure out if it's more convenient/faster to just use SlidingWindow with 60s slide and appropriate trigger?
class AggregatorFunction[T <: AnyRef](aggregator: Aggregator, lengthInMillis: Long, nodeId: String)
  extends LatelyEvictableStateFunction[ValueWithContext[(String, AnyRef)], ValueWithContext[Any], TreeMap[Long, AnyRef]] {

  private val minimalResolutionMs = 60000L

  override protected def stateDescriptor: ValueStateDescriptor[TreeMap[Long, AnyRef]]
  = new ValueStateDescriptor[TreeMap[Long, AnyRef]]("state", classOf[TreeMap[Long, AnyRef]])


  override def processElement(value: ValueWithContext[(String, AnyRef)], ctx: KeyedProcessFunction[String, ValueWithContext[(String, AnyRef)], ValueWithContext[Any]]#Context, out: Collector[ValueWithContext[Any]]): Unit = {

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

  private def computeNewState(newValue: aggregator.Element, ctx: KeyedProcessFunction[String, ValueWithContext[(String, AnyRef)], ValueWithContext[Any]]#Context): TreeMap[Long, aggregator.Aggregate] = {

    val current: TreeMap[Long, aggregator.Aggregate] = currentState(ctx)

    val timestamp = computeTimestampToStore(ctx)

    val valueForTimestamp = current.getOrElse(timestamp, aggregator.createAccumulator())

    current.updated(timestamp, aggregator.add(newValue, valueForTimestamp).asInstanceOf[aggregator.Aggregate])
  }

  private def computeTimestampToStore(ctx: KeyedProcessFunction[String, ValueWithContext[(String, AnyRef)], ValueWithContext[Any]]#Context) = {
    (ctx.timestamp() / minimalResolutionMs) * minimalResolutionMs
  }

  private def currentState(ctx: KeyedProcessFunction[String, ValueWithContext[(String, AnyRef)], ValueWithContext[Any]]#Context): TreeMap[Long, aggregator.Aggregate] = {
    val currentState = Option(state.value().asInstanceOf[TreeMap[Long, aggregator.Aggregate]]).getOrElse(TreeMap[Long, aggregator.Aggregate]()(Ordering.Long))
    currentState.from(ctx.timestamp() - lengthInMillis)
  }
}



