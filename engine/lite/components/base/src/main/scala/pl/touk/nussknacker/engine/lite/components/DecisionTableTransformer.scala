package pl.touk.nussknacker.engine.lite.components

import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, MethodToInvoke, OutputVariableName, ParamName}
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.LiteCustomComponent

object DecisionTableTransformer extends CustomStreamTransformer {

  @MethodToInvoke
  def invoke(
      @ParamName("decisionTable") table: String,
      @OutputVariableName outputVariable: String
  ): LiteCustomComponent = {
    ???
  }

}
