package pl.touk.nussknacker.engine.flink.util.transformer

import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsCompat
import pl.touk.nussknacker.engine.flink.api.process._

case object PreviousValueTransformer extends PreviousValueTransformer {

  override protected def explicitUidInStatefulOperators: Boolean = ExplicitUidInOperatorsCompat.DefaultExplicitUidInStatefulOperators
  
}

abstract class PreviousValueTransformer extends CustomStreamTransformer with ExplicitUidInOperatorsCompat {

  type Value = Any

  @MethodToInvoke(returnType = classOf[Value])
  def execute(@ParamName("keyBy") keyBy: LazyParameter[String],
              @ParamName("value") value: LazyParameter[Value])
  = FlinkCustomStreamTransformation((start: DataStream[Context], ctx: FlinkCustomNodeContext) =>
    setUidToNodeIdIfNeed(ctx)(start
      .map(ctx.lazyParameterHelper.lazyMapFunction(keyBy))
      .keyBy(_.value)
      .map(new PreviousValueFunction(value, ctx.lazyParameterHelper))), value.returnType)

  class PreviousValueFunction(val parameter: LazyParameter[Value],
                              val lazyParameterHelper: FlinkLazyParameterFunctionHelper) extends RichMapFunction[ValueWithContext[String], ValueWithContext[Any]]
    with OneParamLazyParameterFunction[Any] {

    private[this] var state: ValueState[Value] = _

    override def open(c: Configuration): Unit = {
      super.open(c)
      val info = new ValueStateDescriptor[Value]("state", classOf[Any])
      state = getRuntimeContext.getState(info)
    }

    override def map(valueWithContext: ValueWithContext[String]): ValueWithContext[Any] = {
      val currentValue = evaluateParameter(valueWithContext.context)
      val toReturn = Option(state.value()).getOrElse(currentValue)
      state.update(currentValue)
      ValueWithContext(toReturn, valueWithContext.context )
    }

  }
}
