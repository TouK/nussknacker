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

case class MethodBasedComponentDefinitionWithImplementation(
    implementationInvoker: ComponentImplementationInvoker,
    implementation: Any,
    staticDefinition: ComponentStaticDefinition,
    // TODO: it should be removed - instead implementationInvoker should be transformed
    runtimeClass: Class[_]
) extends ComponentDefinitionWithImplementation {

  final override def withImplementationInvoker(
      implementationInvoker: ComponentImplementationInvoker
  ): ComponentDefinitionWithImplementation =
    copy(implementationInvoker = implementationInvoker)

  final override def componentType: ComponentType = staticDefinition.componentType

  final def parameters: List[Parameter] = staticDefinition.parameters

  final override def returnType: Option[TypingResult] = staticDefinition.returnType

  final override def componentTypeSpecificData: ComponentTypeSpecificData = staticDefinition.componentTypeSpecificData

}
