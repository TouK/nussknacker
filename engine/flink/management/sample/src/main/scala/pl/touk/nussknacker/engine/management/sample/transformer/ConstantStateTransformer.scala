package pl.touk.nussknacker.engine.management.sample.transformer

import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.scala.DataStream
import pl.touk.nussknacker.engine.api.{Context, CustomStreamTransformer, MethodToInvoke, QueryableStateNames, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomStreamTransformation
import org.apache.flink.streaming.api.scala._

case class ConstantStateTransformer[T: TypeInformation](defaultValue: T) extends CustomStreamTransformer {

  final val stateName = "constantState"

  @MethodToInvoke
  @QueryableStateNames(values = Array(stateName))
  def execute(): FlinkCustomStreamTransformation = FlinkCustomStreamTransformation((start: DataStream[Context]) => {
    start
      .keyBy(_ => "1")
      .map(new RichMapFunction[Context, ValueWithContext[AnyRef]] {

        var constantState: ValueState[T] = _

        override def open(parameters: Configuration): Unit = {
          super.open(parameters)
          val descriptor = new ValueStateDescriptor[T]("constantState", implicitly[TypeInformation[T]])
          descriptor.setQueryable(stateName)
          constantState = getRuntimeContext.getState(descriptor)
        }

        override def map(value: Context): ValueWithContext[AnyRef] = {
          constantState.update(defaultValue)
          ValueWithContext[AnyRef]("", value)
        }
      }).uid("customStateId")
  })
}
