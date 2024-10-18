package pl.touk.nussknacker.engine.definition.component.dynamic

import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.context.transformation.DynamicComponent
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  ComponentImplementationInvoker,
  ComponentTypeSpecificData,
  ComponentUiDefinition
}

final case class DynamicComponentDefinitionWithImplementation(
    override val name: String,
    override val implementationInvoker: ComponentImplementationInvoker,
    override val component: DynamicComponent[_],
    override val componentTypeSpecificData: ComponentTypeSpecificData,
    override protected val uiDefinition: ComponentUiDefinition,
    parametersConfig: Map[ParameterName, ParameterConfig],
    rawParametersConfig: Map[ParameterName, ParameterConfig]
) extends ComponentDefinitionWithImplementation {

  override def withImplementationInvoker(
      invoker: ComponentImplementationInvoker
  ): ComponentDefinitionWithImplementation =
    copy(implementationInvoker = invoker)

  override protected def typesFromStaticDefinition: List[TypingResult] = List.empty

}
