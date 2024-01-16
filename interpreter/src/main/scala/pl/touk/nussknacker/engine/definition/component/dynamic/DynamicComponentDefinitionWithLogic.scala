package pl.touk.nussknacker.engine.definition.component.dynamic

import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.context.transformation.GenericNodeTransformation
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithLogic,
  ComponentLogic,
  ComponentTypeSpecificData
}

final case class DynamicComponentDefinitionWithLogic(
    override val componentLogic: ComponentLogic,
    component: GenericNodeTransformation[_],
    componentConfig: SingleComponentConfig,
    originalGroupName: ComponentGroupName,
    override val componentTypeSpecificData: ComponentTypeSpecificData
) extends ComponentDefinitionWithLogic {

  override val componentType: ComponentType = componentTypeSpecificData.componentType

  override def withComponentLogic(logic: ComponentLogic): ComponentDefinitionWithLogic =
    copy(componentLogic = logic)

}
