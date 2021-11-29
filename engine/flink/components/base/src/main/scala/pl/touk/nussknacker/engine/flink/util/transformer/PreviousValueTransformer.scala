package pl.touk.nussknacker.engine.flink.util.transformer

import org.apache.flink.api.common.functions.RichFlatMapFunction
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.scala._
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process._

case object PreviousValueTransformer extends CustomStreamTransformer with ExplicitUidInOperatorsSupport {

  type Value = AnyRef

  @MethodToInvoke(returnType = classOf[Value])
  def execute(@ParamName("groupBy") groupBy: LazyParameter[CharSequence],
              @ParamName("value") value: LazyParameter[Value])
  = FlinkCustomStreamTransformation((start: DataStream[Context], ctx: FlinkCustomNodeContext) =>
    setUidToNodeIdIfNeed(ctx,
      start
        .flatMap(ctx.lazyParameterHelper.lazyMapFunction(groupBy))
        .keyBy(vCtx => Option(vCtx.value).map(_.toString).orNull)
        .flatMap(new PreviousValueFunction(value, ctx.lazyParameterHelper))), value.returnType)

  class PreviousValueFunction(val parameter: LazyParameter[Value],
                              val lazyParameterHelper: FlinkLazyParameterFunctionHelper) extends RichFlatMapFunction[ValueWithContext[CharSequence], ValueWithContext[AnyRef]]
    with OneParamLazyParameterFunction[AnyRef] {

    private[this] var state: ValueState[Value] = _

    override def open(c: Configuration): Unit = {
      super.open(c)
      val info = new ValueStateDescriptor[Value]("state", classOf[AnyRef])
      state = getRuntimeContext.getState(info)
    }


    override def flatMap(valueWithContext: ValueWithContext[CharSequence], out: Collector[ValueWithContext[AnyRef]]): Unit = {
      collectHandlingErrors(valueWithContext.context, out) {
        val currentValue = evaluateParameter(valueWithContext.context)
        val toReturn = Option(state.value()).getOrElse(currentValue)
        state.update(currentValue)
        ValueWithContext(toReturn, valueWithContext.context )
      }
    }

  }
}
