package pl.touk.nussknacker.engine.flink.util.transformer

import org.apache.flink.api.common.state.{MapState, MapStateDescriptor}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.ListTypeInfo
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.Component.AllowedProcessingModes
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation}
import pl.touk.nussknacker.engine.flink.util.keyed.StringKeyOnlyMapper

import java.time.Duration
import java.util
import javax.annotation.Nullable

object DelayTransformer extends DelayTransformer

class DelayTransformer extends CustomStreamTransformer with ExplicitUidInOperatorsSupport with Serializable {

  override def allowedProcessingModes: AllowedProcessingModes =
    AllowedProcessingModes.SetOf(ProcessingMode.UnboundedStream, ProcessingMode.BoundedStream)

  @MethodToInvoke(returnType = classOf[Void])
  def invoke(
      @ParamName("key") @Nullable key: LazyParameter[CharSequence],
      @ParamName("delay") delay: Duration
  ): FlinkCustomStreamTransformation =
    FlinkCustomStreamTransformation { (stream: DataStream[Context], nodeCtx: FlinkCustomNodeContext) =>
      val keyedStream =
        Option(key)
          .map { _ =>
            stream
              .flatMap(new StringKeyOnlyMapper(nodeCtx.lazyParameterHelper, key))
              .keyBy((v: ValueWithContext[String]) => v.value)
          }
          .getOrElse {
            stream
              .map(ctx => ValueWithContext(defaultKey(ctx), ctx))
              .keyBy((v: ValueWithContext[String]) => v.value)
          }
      setUidToNodeIdIfNeed(
        nodeCtx,
        keyedStream
          .process(prepareDelayFunction(nodeCtx, delay))
      )
    }

  protected def defaultKey(ctx: Context): String = ""

  protected def prepareDelayFunction(nodeCtx: FlinkCustomNodeContext, delay: Duration): DelayFunction = {
    new DelayFunction(nodeCtx, delay)
  }

}

class DelayFunction(nodeCtx: FlinkCustomNodeContext, delay: Duration)
    extends KeyedProcessFunction[String, ValueWithContext[String], ValueWithContext[AnyRef]] {

  type FlinkCtx      = KeyedProcessFunction[String, ValueWithContext[String], ValueWithContext[AnyRef]]#Context
  type FlinkTimerCtx = KeyedProcessFunction[String, ValueWithContext[String], ValueWithContext[AnyRef]]#OnTimerContext

  private val descriptor = new MapStateDescriptor[Long, java.util.List[api.Context]](
    "state",
    TypeInformation.of(classOf[Long]),
    new ListTypeInfo(nodeCtx.contextTypeInfo)
  )

  @transient private var state: MapState[Long, java.util.List[api.Context]] = _

  override def open(config: Configuration): Unit = {
    state = getRuntimeContext.getMapState(descriptor)
  }

  override def processElement(
      value: ValueWithContext[String],
      ctx: FlinkCtx,
      out: Collector[ValueWithContext[AnyRef]]
  ): Unit = {
    val fireTime = ctx.timestamp() + delay.toMillis

    val currentState = readStateValueOrInitial(fireTime)
    currentState.add(value.context)
    state.put(fireTime, currentState)

    ctx.timerService().registerEventTimeTimer(fireTime)
  }

  override def onTimer(timestamp: Long, funCtx: FlinkTimerCtx, out: Collector[ValueWithContext[AnyRef]]): Unit = {
    val currentState = readStateValueOrInitial(timestamp)
    currentState.forEach(emitValue(out, _))
    state.remove(timestamp)
  }

  protected def emitValue(output: Collector[ValueWithContext[AnyRef]], ctx: api.Context): Unit = {
    output.collect(ValueWithContext(null, ctx))
  }

  private def readStateValueOrInitial(timestamp: Long): java.util.List[api.Context] = {
    Option(state.get(timestamp)).getOrElse(new util.ArrayList[api.Context]())
  }

}
