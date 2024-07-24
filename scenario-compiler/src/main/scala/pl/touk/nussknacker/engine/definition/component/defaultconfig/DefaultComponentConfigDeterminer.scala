package pl.touk.nussknacker.engine.definition.component.defaultconfig

import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.definition.component._

object DefaultComponentConfigDeterminer {

  def forNotBuiltInComponentType(
      componentTypeSpecificData: ComponentTypeSpecificData,
      hasReturn: Boolean
  ): SingleComponentConfig = {
    // TODO: use convention icon = componentGroup instead of code
    val configData = componentTypeSpecificData match {
      case SourceSpecificData =>
        ComponentConfigData(DefaultsComponentGroupName.SourcesGroupName, DefaultsComponentIcon.SourceIcon)
      case SinkSpecificData =>
        ComponentConfigData(DefaultsComponentGroupName.SinksGroupName, DefaultsComponentIcon.SinkIcon)
      case ServiceSpecificData if hasReturn =>
        ComponentConfigData(DefaultsComponentGroupName.EnrichersGroupName, DefaultsComponentIcon.EnricherIcon)
      case ServiceSpecificData =>
        ComponentConfigData(DefaultsComponentGroupName.ServicesGroupName, DefaultsComponentIcon.ServiceIcon)
      case CustomComponentSpecificData(_, true) =>
        ComponentConfigData(
          DefaultsComponentGroupName.OptionalEndingCustomGroupName,
          DefaultsComponentIcon.CustomComponentIcon
        )
      case CustomComponentSpecificData(_, _) =>
        ComponentConfigData(DefaultsComponentGroupName.CustomGroupName, DefaultsComponentIcon.CustomComponentIcon)
      case _ =>
        throw new IllegalStateException(
          s"InitialComponentConfigDeterminer used with non model component: $componentTypeSpecificData"
        )
    }
    SingleComponentConfig(
      params = None,
      icon = Some(configData.icon),
      docsUrl = None,
      componentGroup = Some(configData.componentGroup),
      componentId = None
    )
  }

  def forBuiltInComponent(id: ComponentId): SingleComponentConfig = {
    val componentGroup = if (BuiltInComponentId.FragmentDefinitionComponents.contains(id)) {
      DefaultsComponentGroupName.FragmentsDefinitionGroupName
    } else {
      DefaultsComponentGroupName.BaseGroupName
    }
    SingleComponentConfig(
      params = None,
      icon = Some(DefaultsComponentIcon.forBuiltInComponent(id)),
      // TODO: move from defaultModelConfig.conf to here + convention instead of code
      docsUrl = None,
      componentGroup = Some(componentGroup),
      componentId = Some(DesignerWideComponentId.forBuiltInComponent(id))
    )
  }

  // For fragments, we don't need to return SingleComponentConfig, because this config won't merged with anything else
  // We can just return final, ComponentUiDefinition
  def forFragment(
      docsUrl: Option[String],
      componentGroupName: Option[ComponentGroupName],
      translateGroupName: ComponentGroupName => Option[ComponentGroupName],
      designerWideId: DesignerWideComponentId,
  ): ComponentUiDefinition = {
    val beforeTranslationGroupName = componentGroupName.getOrElse(DefaultsComponentGroupName.FragmentsGroupName)

    ComponentUiDefinition(
      originalGroupName = beforeTranslationGroupName,
      componentGroup = translateGroupName(beforeTranslationGroupName)
        .getOrElse(throw new IllegalStateException("Fragments can't be assigned to the null component group")),
      icon = DefaultsComponentIcon.FragmentIcon,
      docsUrl = docsUrl,
      designerWideId = designerWideId
    )
  }

  private case class ComponentConfigData(componentGroup: ComponentGroupName, icon: String)

}
