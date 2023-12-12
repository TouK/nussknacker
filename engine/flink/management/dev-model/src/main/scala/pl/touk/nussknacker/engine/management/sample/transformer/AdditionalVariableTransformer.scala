package pl.touk.nussknacker.engine.management.sample.transformer

import org.apache.flink.streaming.api.datastream.DataStream
import pl.touk.nussknacker.engine.api.{
  AdditionalVariable,
  AdditionalVariables,
  CustomStreamTransformer,
  LazyParameter,
  MethodToInvoke,
  ParamName,
  ScenarioProcessingContext,
  ValueWithContext
}
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomStreamTransformation

object AdditionalVariableTransformer extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[Void])
  def execute(
      @AdditionalVariables(Array(new AdditionalVariable(name = "additional", clazz = classOf[String]))) @ParamName(
        "expression"
      ) expression: LazyParameter[java.lang.Boolean]
  ) =
    FlinkCustomStreamTransformation((start: DataStream[ScenarioProcessingContext]) =>
      start.map(ValueWithContext[AnyRef]("", _))
    )

}
