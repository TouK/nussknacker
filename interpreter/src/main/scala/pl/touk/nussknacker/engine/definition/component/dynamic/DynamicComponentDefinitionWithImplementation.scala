package pl.touk.nussknacker.engine.definition.component.dynamic

import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.context.transformation.GenericNodeTransformation
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
    override val implementation: GenericNodeTransformation[_],
    override val componentTypeSpecificData: ComponentTypeSpecificData,
    override protected val uiDefinition: ComponentUiDefinition,
    parametersConfig: Map[String, ParameterConfig]
) extends ComponentDefinitionWithImplementation {

  override def withImplementationInvoker(
      implementationInvoker: ComponentImplementationInvoker
  ): ComponentDefinitionWithImplementation =
    copy(implementationInvoker = implementationInvoker)

  override protected def typesFromStaticDefinition: List[TypingResult] = List.empty

}
