package pl.touk.nussknacker.engine.definition.component

import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

final case class ComponentStaticDefinition(
    parameters: List[Parameter],
    returnType: Option[TypingResult],
    parametersWithoutEnrichments: List[Parameter]
) {
  def hasReturn: Boolean = returnType.isDefined
}

object ComponentStaticDefinition {

  def apply(parameters: List[Parameter], returnType: Option[TypingResult]): ComponentStaticDefinition = {
    new ComponentStaticDefinition(parameters, returnType, parameters)
  }

}
