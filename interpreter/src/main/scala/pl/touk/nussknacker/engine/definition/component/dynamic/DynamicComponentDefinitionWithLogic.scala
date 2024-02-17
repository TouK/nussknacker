package pl.touk.nussknacker.engine.definition.component.dynamic

import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.context.transformation.DynamicComponent
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithLogic,
  ComponentLogic,
  ComponentTypeSpecificData,
  ComponentUiDefinition
}

final case class DynamicComponentDefinitionWithLogic(
    override val name: String,
    override val componentLogic: ComponentLogic,
    override val component: DynamicComponent[_],
    override val componentTypeSpecificData: ComponentTypeSpecificData,
    override protected val uiDefinition: ComponentUiDefinition,
    parametersConfig: Map[String, ParameterConfig]
) extends ComponentDefinitionWithLogic {

  override def withComponentLogic(
      logic: ComponentLogic
  ): ComponentDefinitionWithLogic =
    copy(componentLogic = logic)

  override protected def typesFromStaticDefinition: List[TypingResult] = List.empty

}
