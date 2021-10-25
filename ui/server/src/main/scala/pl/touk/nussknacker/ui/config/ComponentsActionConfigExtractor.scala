package pl.touk.nussknacker.ui.config

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.{OptionReader, ValueReader}
import pl.touk.nussknacker.engine.api.component.ComponentId
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.restmodel.component.ComponentAction

case class ComponentActionConfig(id: String, title: String, icon: String, url: Option[String], supportedComponentTypes: Option[List[ComponentType]]) {
  import ComponentActionConfig._

  def isAvailable(componentType: ComponentType): Boolean = supportedComponentTypes.isEmpty || supportedComponentTypes.exists(_.contains(componentType))

  def toComponentAction(componentId: ComponentId, componentName: String): ComponentAction = ComponentAction(
      id,
      fillByComponentData(title, componentId, componentName),
      fillByComponentData(icon, componentId, componentName),
      url.map(u => fillByComponentData(u, componentId, componentName))
    )
}

object ComponentActionConfig {
  val ComponentIdTemplate = "$componentId"
  val ComponentNameTemplate = "$componentName"

  def fillByComponentData(text: String, componentId: ComponentId, componentName: String): String = {
    text
      .replace(ComponentIdTemplate, componentId.value)
      .replace(ComponentNameTemplate, componentName)
  }
}

object ComponentActionsConfigExtractor {

  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._

  type ComponentActionsConfig = List[ComponentActionConfig]

  private val ComponentsActionNamespace = "componentActions"

  implicit val optionListReader: ValueReader[Option[ComponentActionsConfig]] = (config: Config, path: String) =>
    OptionReader
      .optionValueReader[List[Config]]
      .read(config, path)
      .map(_.map(_.as[ComponentActionConfig]))

  def extract(config: Config): ComponentActionsConfig =
    config.as[Option[ComponentActionsConfig]](ComponentsActionNamespace)
      .getOrElse(List.empty)

}
