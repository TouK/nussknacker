package pl.touk.nussknacker.engine.definition.component

import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

case class ComponentStaticDefinition(parameters: List[Parameter], returnType: Option[TypingResult]) {
  def hasReturn: Boolean = returnType.isDefined
}
