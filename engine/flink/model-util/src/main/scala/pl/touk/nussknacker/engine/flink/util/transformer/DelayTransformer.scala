package pl.touk.nussknacker.engine.flink.util.transformer

import java.time.Duration

import javax.annotation.Nullable
import org.apache.flink.api.common.state.{MapState, MapStateDescriptor}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation}
import pl.touk.nussknacker.engine.flink.util.keyed.StringKeyOnlyMapper

object DelayTransformer extends DelayTransformer

class DelayTransformer extends CustomStreamTransformer with ExplicitUidInOperatorsSupport {

  @MethodToInvoke(returnType = classOf[Void])
  def invoke(@ParamName("key") @Nullable key: LazyParameter[CharSequence],
             @ParamName("delay") delay: Duration): FlinkCustomStreamTransformation =
    FlinkCustomStreamTransformation { (stream: DataStream[Context], nodeCtx: FlinkCustomNodeContext) =>
      val keyedStream =
        Option(key).map { _ =>
          stream
            .flatMap(new StringKeyOnlyMapper(nodeCtx.lazyParameterHelper, key))
            .keyBy(_.value)
        }.getOrElse {
          stream
            .map(ctx => ValueWithContext(defaultKey(ctx), ctx))
            .keyBy(_.value)
        }
      setUidToNodeIdIfNeed(nodeCtx, keyedStream
        .process(prepareDelayFunction(nodeCtx.nodeId, delay)))
    }

  protected def defaultKey(ctx: Context): String = ""

  protected def prepareDelayFunction(nodeId: String, delay: Duration): DelayFunction = {
    new DelayFunction(delay)
  }

}

class DelayFunction(delay: Duration)
  extends KeyedProcessFunction[String, ValueWithContext[String], ValueWithContext[AnyRef]] {

  type FlinkCtx = KeyedProcessFunction[String, ValueWithContext[String], ValueWithContext[AnyRef]]#Context
  type FlinkTimerCtx = KeyedProcessFunction[String, ValueWithContext[String], ValueWithContext[AnyRef]]#OnTimerContext

  private val descriptor = new MapStateDescriptor[Long, List[api.Context]]("state", implicitly[TypeInformation[Long]], implicitly[TypeInformation[List[api.Context]]])

  @transient private var state : MapState[Long, List[api.Context]] = _

  override def open(config: Configuration): Unit = {
    state = getRuntimeContext.getMapState(descriptor)
  }

  override def processElement(value: ValueWithContext[String], ctx: FlinkCtx, out: Collector[ValueWithContext[AnyRef]]): Unit = {
    val fireTime = ctx.timestamp() + delay.toMillis

    val currentState = readStateValueOrInitial(fireTime)
    val stateWithNewEntry = value.context :: currentState
    state.put(fireTime, stateWithNewEntry)
    
    ctx.timerService().registerEventTimeTimer(fireTime)
  }

  override def onTimer(timestamp: Long, funCtx: FlinkTimerCtx, out: Collector[ValueWithContext[AnyRef]]): Unit = {
    val currentState = readStateValueOrInitial(timestamp)
    currentState.reverse.foreach(emitValue(out, _))
    state.remove(timestamp)
  }

  protected def emitValue(output: Collector[ValueWithContext[AnyRef]], ctx: api.Context): Unit = {
    output.collect(ValueWithContext(null, ctx))
  }

  private def readStateValueOrInitial(timestamp: Long) : List[api.Context] = {
    Option(state.get(timestamp)).getOrElse(List.empty)
  }

}
