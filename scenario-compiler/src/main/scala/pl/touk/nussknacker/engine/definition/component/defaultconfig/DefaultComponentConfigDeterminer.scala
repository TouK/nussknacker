package pl.touk.nussknacker.engine.definition.component.defaultconfig

import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.definition.component._

object DefaultComponentConfigDeterminer {

  def forNotBuiltInComponentType(
      componentType: ComponentType,
      hasReturn: Boolean,
      customCanBeEnding: Option[Boolean]
  ): ComponentConfig = {
    // TODO: use convention icon = componentGroup instead of code
    val configData = componentType match {
      case ComponentType.Source =>
        ComponentConfigData(DefaultsComponentGroupName.SourcesGroupName, DefaultsComponentIcon.SourceIcon)
      case ComponentType.Sink =>
        ComponentConfigData(DefaultsComponentGroupName.SinksGroupName, DefaultsComponentIcon.SinkIcon)
      case ComponentType.Service if hasReturn =>
        ComponentConfigData(DefaultsComponentGroupName.EnrichersGroupName, DefaultsComponentIcon.EnricherIcon)
      case ComponentType.Service =>
        ComponentConfigData(DefaultsComponentGroupName.ServicesGroupName, DefaultsComponentIcon.ServiceIcon)
      case ComponentType.CustomComponent if customCanBeEnding.contains(true) =>
        ComponentConfigData(
          DefaultsComponentGroupName.OptionalEndingCustomGroupName,
          DefaultsComponentIcon.CustomComponentIcon
        )
      case ComponentType.CustomComponent =>
        ComponentConfigData(DefaultsComponentGroupName.CustomGroupName, DefaultsComponentIcon.CustomComponentIcon)
      case ComponentType.BuiltIn =>
        throw new IllegalStateException(
          s"DefaultComponentConfigDeterminer used with built-in component"
        )
    }
    ComponentConfig(
      params = None,
      icon = Some(configData.icon),
      docsUrl = None,
      componentGroup = Some(configData.componentGroup),
      componentId = None,
      label = None
    )
  }

  def forBuiltInComponent(id: ComponentId): ComponentConfig = {
    val componentGroup = if (BuiltInComponentId.FragmentDefinitionComponents.contains(id)) {
      DefaultsComponentGroupName.FragmentsDefinitionGroupName
    } else {
      DefaultsComponentGroupName.BaseGroupName
    }
    ComponentConfig(
      params = None,
      icon = Some(DefaultsComponentIcon.forBuiltInComponent(id)),
      // TODO: move from defaultModelConfig.conf to here + convention instead of code
      docsUrl = None,
      componentGroup = Some(componentGroup),
      componentId = Some(DesignerWideComponentId.forBuiltInComponent(id)),
      label = Some(id.name.replace("-", " "))
    )
  }

  // For fragments, we don't need to return SingleComponentConfig, because this config won't merged with anything else
  // We can just return final, ComponentUiDefinition
  def forFragment(
      docsUrl: Option[String],
      componentGroupName: Option[ComponentGroupName],
      icon: Option[String],
      translateGroupName: ComponentGroupName => Option[ComponentGroupName],
      designerWideId: DesignerWideComponentId,
  ): ComponentUiDefinition = {
    val beforeTranslationGroupName = componentGroupName.getOrElse(DefaultsComponentGroupName.FragmentsGroupName)

    ComponentUiDefinition(
      originalGroupName = beforeTranslationGroupName,
      componentGroup = translateGroupName(beforeTranslationGroupName)
        .getOrElse(throw new IllegalStateException("Fragments can't be assigned to the null component group")),
      icon = icon.getOrElse(DefaultsComponentIcon.FragmentIcon),
      docsUrl = docsUrl,
      designerWideId = designerWideId,
      label = None
    )
  }

  private case class ComponentConfigData(componentGroup: ComponentGroupName, icon: String)

}
