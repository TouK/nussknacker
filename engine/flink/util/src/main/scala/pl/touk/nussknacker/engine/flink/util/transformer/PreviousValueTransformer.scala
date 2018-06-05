package pl.touk.nussknacker.engine.flink.util.transformer

import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomStreamTransformation

object PreviousValueTransformer extends CustomStreamTransformer {

  type Value = AnyRef

  @MethodToInvoke(returnType = classOf[Value])
  def execute(@ParamName("keyBy") keyBy: LazyInterpreter[String],
              @ParamName("value") value: LazyInterpreter[Value])
  = FlinkCustomStreamTransformation((start: DataStream[InterpretationResult]) => {

    start.keyBy(keyBy.syncInterpretationFunction)
      .mapWithState[ValueWithContext[Any], Value] { case (ir, previousValue) =>
      val currentValue = value.syncInterpretationFunction(ir)
      (ValueWithContext(previousValue.getOrElse(currentValue), ir.finalContext), Some(currentValue))
    }

  })

}