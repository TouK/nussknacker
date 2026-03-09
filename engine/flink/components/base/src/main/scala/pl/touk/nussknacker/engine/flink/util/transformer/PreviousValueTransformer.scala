package pl.touk.nussknacker.engine.flink.util.transformer

import org.apache.flink.api.common.functions.{OpenContext, RichFlatMapFunction}
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.flink.api.datastream.DataStreamImplicits.DataStreamExtension
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.flink.util.richflink.FlinkKeyOperations

case object PreviousValueTransformer extends CustomStreamTransformer {

  type Value = AnyRef

  private val keyParameterName = ParameterName("Key")

  @MethodToInvoke(returnType = classOf[Value])
  def execute(
      @ParamName("Key") key: LazyParameter[CharSequence],
      @ParamName("Value") value: LazyParameter[Value]
  ): FlinkCustomStreamTransformation with ReturningType = FlinkCustomStreamTransformation(
    (start: DataStream[Context], ctx: FlinkCustomNodeContext) => {
      val valueTypeInfo = TypeInformationDetection.instance.forType[AnyRef](value.returnType)
      start
        .groupBy(key, keyParameterName)(ctx)
        .flatMap(
          new PreviousValueFunction(value, ctx.lazyParameterHelper, valueTypeInfo),
          ctx.valueWithContextInfo.forType(valueTypeInfo)
        )
        .setUidAndName(ctx.nodeId.value, ctx.nodeName.value)
    },
    value.returnType
  )

  class PreviousValueFunction(
      val parameter: LazyParameter[Value],
      val lazyParameterHelper: FlinkLazyParameterFunctionHelper,
      typeInformation: TypeInformation[Value]
  ) extends RichFlatMapFunction[ValueWithContext[String], ValueWithContext[AnyRef]]
      with OneParamLazyParameterFunction[AnyRef] {

    private[this] var state: ValueState[Value] = _

    override def open(openContext: OpenContext): Unit = {
      super.open(openContext)
      val info = new ValueStateDescriptor[Value]("state", typeInformation)
      state = getRuntimeContext.getState(info)
    }

    override def flatMap(valueWithContext: ValueWithContext[String], out: Collector[ValueWithContext[AnyRef]]): Unit = {
      collectHandlingErrors(valueWithContext.context, out) {
        val currentValue = evaluateParameter(valueWithContext.context)
        val toReturn     = Option(state.value()).getOrElse(currentValue)
        state.update(currentValue)
        ValueWithContext(toReturn, valueWithContext.context)
      }
    }

  }

}
