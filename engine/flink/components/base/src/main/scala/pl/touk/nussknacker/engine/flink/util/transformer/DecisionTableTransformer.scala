package pl.touk.nussknacker.engine.flink.util.transformer

import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.{
  CustomStreamTransformer,
  LazyParameter,
  MethodToInvoke,
  OutputVariableName,
  ParamName
}
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomStreamTransformation

object DecisionTableTransformer extends CustomStreamTransformer with Serializable {

  @MethodToInvoke
  def invoke(
      @ParamName("Basic Decision Table") table: String,
      @ParamName("Expression") expression: LazyParameter[java.lang.Boolean],
      @OutputVariableName outputVariable: String
  ): FlinkCustomStreamTransformation with ReturningType = {
    ???
  }

}
