package pl.touk.nussknacker.engine.definition.component.dynamic

import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.context.transformation.GenericNodeTransformation
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  ComponentImplementationInvoker,
  ComponentTypeSpecificData
}

final case class DynamicComponentDefinitionWithImplementation(
    override val implementationInvoker: ComponentImplementationInvoker,
    implementation: GenericNodeTransformation[_],
    componentConfig: SingleComponentConfig,
    originalGroupName: ComponentGroupName,
    override val componentTypeSpecificData: ComponentTypeSpecificData
) extends ComponentDefinitionWithImplementation {

  override def withImplementationInvoker(
      implementationInvoker: ComponentImplementationInvoker
  ): ComponentDefinitionWithImplementation =
    copy(implementationInvoker = implementationInvoker)

  override def componentType: ComponentType = componentTypeSpecificData.componentType

}
