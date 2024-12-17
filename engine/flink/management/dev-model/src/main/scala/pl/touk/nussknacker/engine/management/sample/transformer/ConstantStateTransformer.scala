package pl.touk.nussknacker.engine.management.sample.transformer

import org.apache.flink.api.common.functions.{OpenContext, RichMapFunction}
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.datastream.DataStream
import pl.touk.nussknacker.engine.api.{Context, CustomStreamTransformer, MethodToInvoke, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomStreamTransformation

//FIXME: remove?
case class ConstantStateTransformer[T: TypeInformation](defaultValue: T) extends CustomStreamTransformer {

  final val stateName = "constantState"

  @MethodToInvoke
  def execute(): FlinkCustomStreamTransformation =
    FlinkCustomStreamTransformation((start: DataStream[Context]) => {
      start
        .keyBy((_: Context) => "1")
        .map(new RichMapFunction[Context, ValueWithContext[AnyRef]] {

          var constantState: ValueState[T] = _

          override def open(openContext: OpenContext): Unit = {
            super.open(openContext)
            val descriptor = new ValueStateDescriptor[T]("constantState", implicitly[TypeInformation[T]])
            constantState = getRuntimeContext.getState(descriptor)
          }

          override def map(value: Context): ValueWithContext[AnyRef] = {
            constantState.update(defaultValue)
            ValueWithContext[AnyRef]("", value)
          }
        })
        .uid("customStateId")
    })

}
