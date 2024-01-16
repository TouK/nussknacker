package pl.touk.nussknacker.engine.definition.component.methodbased

import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithLogic,
  ComponentLogic,
  ComponentStaticDefinition,
  ComponentTypeSpecificData
}

final case class MethodBasedComponentDefinitionWithLogic(
    componentLogic: ComponentLogic,
    component: Any,
    staticDefinition: ComponentStaticDefinition
) extends ComponentDefinitionWithLogic {

  override def withComponentLogic(
      implementationInvoker: ComponentLogic
  ): ComponentDefinitionWithLogic =
    copy(componentLogic = implementationInvoker)

  override def componentType: ComponentType = staticDefinition.componentType

  def parameters: List[Parameter] = staticDefinition.parameters

  def returnType: Option[TypingResult] = staticDefinition.returnType

  override def componentTypeSpecificData: ComponentTypeSpecificData = staticDefinition.componentTypeSpecificData

}
