package pl.touk.nussknacker.engine.definition.component.dynamic

import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.context.transformation.DynamicComponent
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.definition.component.{
  ComponentRuntimeLogicFactory,
  ComponentTypeSpecificData,
  ComponentUiDefinition,
  ComponentWithDefinition
}

final case class DynamicComponentWithDefinition(
    override val name: String,
    override val runtimeLogicFactory: ComponentRuntimeLogicFactory,
    override val component: DynamicComponent[_],
    override val componentTypeSpecificData: ComponentTypeSpecificData,
    override protected val uiDefinition: ComponentUiDefinition,
    parametersConfig: Map[String, ParameterConfig]
) extends ComponentWithDefinition {

  override def withRuntimeLogicFactory(
      runtimeLogicFactory: ComponentRuntimeLogicFactory
  ): ComponentWithDefinition =
    copy(runtimeLogicFactory = runtimeLogicFactory)

  override protected def typesFromStaticDefinition: List[TypingResult] = List.empty

}
