package pl.touk.nussknacker.engine.definition.component

import pl.touk.nussknacker.engine.api.component.SingleComponentConfig
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

case class ComponentStaticDefinition(
    parameters: List[Parameter],
    returnType: Option[TypingResult],
    categories: Option[List[String]],
    componentConfig: SingleComponentConfig
) {

  def withComponentConfig(componentConfig: SingleComponentConfig): ComponentStaticDefinition =
    copy(componentConfig = componentConfig)

  val hasNoReturn: Boolean = returnType.isEmpty

}
