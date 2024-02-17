package pl.touk.nussknacker.engine.definition.fragment

import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, DesignerWideComponentId}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.definition.component.defaultconfig.DefaultComponentConfigDeterminer
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithLogic
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithLogic,
  ComponentLogic,
  ComponentStaticDefinition,
  FragmentSpecificData
}

object FragmentComponentDefinition {

  def apply(
      name: String,
      componentLogic: ComponentLogic,
      parameters: List[Parameter],
      outputNames: List[String],
      docsUrl: Option[String],
      translateGroupName: ComponentGroupName => Option[ComponentGroupName],
      designerWideId: DesignerWideComponentId,
  ): ComponentDefinitionWithLogic = {
    val uiDefinition =
      DefaultComponentConfigDeterminer.forFragment(docsUrl, translateGroupName, designerWideId)
    // Currently fragments are represented as method-based component, probably we should change it to some dedicated type
    MethodBasedComponentDefinitionWithLogic(
      name = name,
      componentLogic = componentLogic,
      component = null,
      componentTypeSpecificData = FragmentSpecificData(outputNames),
      staticDefinition = ComponentStaticDefinition(
        parameters,
        Some(Typed.genericTypeClass[java.util.Map[_, _]](List(Typed[String], Unknown)))
      ),
      uiDefinition = uiDefinition
    )
  }

}
