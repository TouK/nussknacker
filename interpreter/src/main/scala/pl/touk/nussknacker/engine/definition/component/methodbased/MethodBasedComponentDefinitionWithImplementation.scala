package pl.touk.nussknacker.engine.definition.component.methodbased

import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  ComponentImplementationInvoker,
  ComponentStaticDefinition,
  ComponentTypeSpecificData
}

final case class MethodBasedComponentDefinitionWithImplementation(
    implementationInvoker: ComponentImplementationInvoker,
    implementation: Any,
    staticDefinition: ComponentStaticDefinition
) extends ComponentDefinitionWithImplementation {

  override def withImplementationInvoker(
      implementationInvoker: ComponentImplementationInvoker
  ): ComponentDefinitionWithImplementation =
    copy(implementationInvoker = implementationInvoker)

  override def componentType: ComponentType = staticDefinition.componentType

  def parameters: List[Parameter] = staticDefinition.parameters

  def returnType: Option[TypingResult] = staticDefinition.returnType

  override def componentTypeSpecificData: ComponentTypeSpecificData = staticDefinition.componentTypeSpecificData

}
