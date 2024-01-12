package pl.touk.nussknacker.engine.definition.component.defaultconfig

import pl.touk.nussknacker.engine.api.component.{
  BuiltInComponentInfo,
  ComponentGroupName,
  ComponentInfo,
  SingleComponentConfig
}
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

  def forBuiltInComponent(info: ComponentInfo): SingleComponentConfig = {
    val componentGroup = if (BuiltInComponentInfo.FragmentDefinitionComponents.contains(info)) {
      DefaultsComponentGroupName.FragmentsDefinitionGroupName
    } else {
      DefaultsComponentGroupName.BaseGroupName
    }
    SingleComponentConfig(
      params = None,
      Some(DefaultsComponentIcon.forBuiltInComponent(info)),
      // TODO: move from defaultModelConfig.conf to here + convention instead of code
      docsUrl = None,
      componentGroup = Some(componentGroup),
      componentId = None
    )
  }

  def forFragment(docsUrl: Option[String]): SingleComponentConfig = SingleComponentConfig(
    params = None,
    Some(DefaultsComponentIcon.FragmentIcon),
    docsUrl = docsUrl,
    componentGroup = Some(DefaultsComponentGroupName.FragmentsGroupName),
    componentId = None
  )

  private case class ComponentConfigData(componentGroup: ComponentGroupName, icon: String)

}
