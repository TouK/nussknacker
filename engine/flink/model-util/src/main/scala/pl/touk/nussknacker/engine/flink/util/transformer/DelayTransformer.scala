package pl.touk.nussknacker.engine.flink.util.transformer


import java.time.Duration

import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.runtime.operators.windowing.TimestampedValue
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation}
import pl.touk.nussknacker.engine.flink.util.keyed.StringKeyOnlyMapper

object DelayTransformer extends CustomStreamTransformer with ExplicitUidInOperatorsSupport {

  @MethodToInvoke(returnType = classOf[Void])
  def invoke(@ParamName("key") key: LazyParameter[CharSequence],
             @ParamName("delay") delay: Duration): FlinkCustomStreamTransformation =
    FlinkCustomStreamTransformation { (stream: DataStream[Context], nodeCtx: FlinkCustomNodeContext) =>
      setUidToNodeIdIfNeed(nodeCtx, stream
        .map(new StringKeyOnlyMapper(nodeCtx.lazyParameterHelper, key))
        .keyBy(_.value)
        .process(prepareDelayFunction(nodeCtx.nodeId, delay)))
    }

  protected def prepareDelayFunction(nodeId: String, delay: Duration): DelayFunction = {
    new DelayFunction(nodeId, delay)
  }

}

class DelayFunction(nodeId: String, delay: Duration)
  extends KeyedProcessFunction[String, ValueWithContext[String], ValueWithContext[AnyRef]] {

  type StateType = List[TimestampedValue[api.Context]]
  type FlinkCtx = KeyedProcessFunction[String, ValueWithContext[String], ValueWithContext[AnyRef]]#Context
  type FlinkTimerCtx = KeyedProcessFunction[String, ValueWithContext[String], ValueWithContext[AnyRef]]#OnTimerContext

  private val descriptor = new ValueStateDescriptor[StateType]("state", classOf[StateType])

  @transient private var state : ValueState[StateType] = _

  override def open(config: Configuration): Unit = {
    state = getRuntimeContext.getState(descriptor)
  }

  override def processElement(value: ValueWithContext[String], ctx: FlinkCtx, out: Collector[ValueWithContext[AnyRef]]): Unit = {
    val fireTime = ctx.timestamp() + delay.toMillis

    val currentState = readStateValueOrInitial()
    val newEntry = new TimestampedValue(value.context, fireTime)
    val stateWithNewEntry = newEntry :: currentState
    state.update(stateWithNewEntry)
    
    ctx.timerService().registerEventTimeTimer(fireTime)
  }

  override def onTimer(timestamp: Long, funCtx: FlinkTimerCtx, out: Collector[ValueWithContext[AnyRef]]): Unit = {
    val currentState = readStateValueOrInitial()
    val valuesLeft = emitValuesEarlierThanTimestamp(timestamp, currentState, out)
    state.update(if (valuesLeft.isEmpty) null else valuesLeft)
  }

  private def emitValuesEarlierThanTimestamp(timestamp: Long, currentState: StateType, output: Collector[ValueWithContext[AnyRef]]): StateType = {
    val (valuesToEmit, valuesLeft) = currentState.partition(_.getTimestamp <= timestamp)
    valuesToEmit.foreach(emitValue(output, _))
    valuesLeft
  }

  protected def emitValue(output: Collector[ValueWithContext[AnyRef]],
                          timestampedCtx: TimestampedValue[api.Context]): Unit = {
    output.collect(ValueWithContext(null, timestampedCtx.getValue))
  }

  private def readStateValueOrInitial() : StateType = {
    Option(state.value()).getOrElse(List.empty)
  }

}
