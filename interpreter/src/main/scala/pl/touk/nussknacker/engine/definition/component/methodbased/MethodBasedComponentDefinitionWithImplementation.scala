package pl.touk.nussknacker.engine.definition.component.methodbased

import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.SingleComponentConfig
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  ComponentImplementationInvoker,
  ComponentStaticDefinition,
  ComponentTypeSpecificData
}

case class MethodBasedComponentDefinitionWithImplementation(
    implementationInvoker: ComponentImplementationInvoker,
    implementation: Any,
    staticDefinition: ComponentStaticDefinition,
    // TODO: it should be removed - instead implementationInvoker should be transformed
    runtimeClass: Class[_]
) extends ComponentDefinitionWithImplementation {

  override def withImplementationInvoker(
      implementationInvoker: ComponentImplementationInvoker
  ): ComponentDefinitionWithImplementation =
    copy(implementationInvoker = implementationInvoker)

  override def componentType: ComponentType = staticDefinition.componentType

  def parameters: List[Parameter] = staticDefinition.parameters

  override def returnType: Option[TypingResult] = staticDefinition.returnType

  override protected[definition] def categories: Option[List[String]] = staticDefinition.categories

  override def componentConfig: SingleComponentConfig = staticDefinition.componentConfig

  override def componentTypeSpecificData: ComponentTypeSpecificData = staticDefinition.componentTypeSpecificData

}
