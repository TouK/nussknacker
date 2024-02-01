package pl.touk.nussknacker.engine.modelconfig

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.engine.api.component.{
  ComponentGroupName,
  ComponentId,
  DesignerWideComponentId,
  SingleComponentConfig
}

object ComponentsUiConfigParser {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.util.config.FicusReaders._

  private implicit val componentsUiGroupNameReader: ValueReader[ComponentGroupName] =
    ValueReader[String].map(ComponentGroupName(_))

  private implicit val componentsUiComponentIdReader: ValueReader[DesignerWideComponentId] =
    ValueReader[String].map(DesignerWideComponentId.apply)

  implicit val componentsGroupNameReader: ValueReader[Map[ComponentGroupName, Option[ComponentGroupName]]] =
    ValueReader[Map[String, Option[String]]]
      .map { mapping =>
        mapping.map { case (key, value) =>
          ComponentGroupName(key) -> value.map(ComponentGroupName(_))
        }
      }

  private val ComponentsUiConfigPath = "componentsUiConfig"

  private val MappingNamespace = "componentsGroupMapping"

  def parse(config: Config): ComponentsUiConfig = {
    val componentsConfig = config.getOrElse[Map[String, SingleComponentConfig]](ComponentsUiConfigPath, Map.empty)
    val groupNameMapping =
      config.getOrElse[Map[ComponentGroupName, Option[ComponentGroupName]]](MappingNamespace, Map.empty)
    new ComponentsUiConfig(componentsConfig, groupNameMapping)
  }

}

class ComponentsUiConfig(
    componentsConfig: Map[String, SingleComponentConfig],
    groupNameMapping: Map[ComponentGroupName, Option[ComponentGroupName]]
) {

  def getConfig(id: ComponentId): SingleComponentConfig = {
    componentsConfig
      .get(id.toString)
      // Should we still support lookup by name?
      .orElse(componentsConfig.get(id.name))
      .getOrElse(SingleComponentConfig.zero)
  }

  // None mean, special "null" group name which hides components
  def groupName(groupName: ComponentGroupName): Option[ComponentGroupName] =
    groupNameMapping.getOrElse(groupName, Some(groupName))

}

object ComponentsUiConfig {

  val Empty: ComponentsUiConfig = new ComponentsUiConfig(Map.empty, Map.empty)

}
