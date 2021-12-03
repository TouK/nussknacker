package pl.touk.nussknacker.ui.config

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.{OptionReader, ValueReader}
import pl.touk.nussknacker.engine.api.component.ComponentId
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.util.UriUtils
import pl.touk.nussknacker.restmodel.component.ComponentLink

import java.net.{URI, URL}

case class ComponentLinkConfig(id: String, title: String, icon: URI, url: URL, supportedComponentTypes: Option[List[ComponentType]]) {
  import ComponentLinkConfig._

  def isAvailable(componentType: ComponentType): Boolean = supportedComponentTypes.isEmpty || supportedComponentTypes.exists(_.contains(componentType))

  def toComponentLink(componentId: ComponentId, componentName: String): ComponentLink = ComponentLink(
      id,
      fillByComponentData(title, componentId, componentName),
      URI.create(fillByComponentData(icon.toString, componentId, componentName, urlOption = true)),
      new URL(fillByComponentData(url.toString, componentId, componentName, urlOption = true))
    )
}

object ComponentLinkConfig {
  val ComponentIdTemplate = "$componentId"
  val ComponentNameTemplate = "$componentName"

  def create(id: String, title: String, icon: String, url: String, supportedComponentTypes: Option[List[ComponentType]]): ComponentLinkConfig =
    ComponentLinkConfig(id, title, URI.create(icon), new URL(url), supportedComponentTypes)

  def fillByComponentData(text: String, componentId: ComponentId, componentName: String, urlOption: Boolean = false): String = {
    val name = if (urlOption) UriUtils.encodeURIComponent(componentName) else componentName

    text
      .replace(ComponentIdTemplate, componentId.value)
      .replace(ComponentNameTemplate, name)
  }
}

object ComponentLinksConfigExtractor {

  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._

  type ComponentLinksConfig = List[ComponentLinkConfig]

  private val ComponentsLinkNamespace = "componentLinks"

  implicit val optionListReader: ValueReader[Option[ComponentLinksConfig]] = (config: Config, path: String) =>
    OptionReader
      .optionValueReader[List[Config]]
      .read(config, path)
      .map(_.map(_.as[ComponentLinkConfig]))

  def extract(config: Config): ComponentLinksConfig =
    config.as[Option[ComponentLinksConfig]](ComponentsLinkNamespace)
      .getOrElse(List.empty)

}
