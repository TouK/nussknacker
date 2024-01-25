package pl.touk.nussknacker.ui.config

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.{OptionReader, ValueReader}
import pl.touk.nussknacker.engine.api.component.DesignerWideComponentId
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.util.UriUtils
import pl.touk.nussknacker.restmodel.component.ComponentLink

import java.net.URI

final case class ComponentLinkConfig(
    id: String,
    title: String,
    icon: URI,
    url: URI,
    // FIXME: It should be probably supportedComponentIds - currently this filtering is unusable
    supportedComponentTypes: Option[List[ComponentType]]
) {
  import ComponentLinkConfig._

  def isAvailable(componentType: ComponentType): Boolean =
    supportedComponentTypes.isEmpty || supportedComponentTypes.exists(_.contains(componentType))

  def toComponentLink(designerWideComponentId: DesignerWideComponentId, componentName: String): ComponentLink =
    ComponentLink(
      id,
      fillByComponentData(title, designerWideComponentId, componentName),
      URI.create(fillByComponentData(icon.toString, designerWideComponentId, componentName, urlOption = true)),
      URI.create(fillByComponentData(url.toString, designerWideComponentId, componentName, urlOption = true))
    )

}

object ComponentLinkConfig {
  val ComponentIdTemplate   = "$componentId"
  val ComponentNameTemplate = "$componentName"

  private def fillByComponentData(
      text: String,
      designerWideComponentId: DesignerWideComponentId,
      componentName: String,
      urlOption: Boolean = false
  ): String = {
    val name = if (urlOption) UriUtils.encodeURIComponent(componentName) else componentName

    text
      .replace(ComponentIdTemplate, designerWideComponentId.value)
      .replace(ComponentNameTemplate, name)
  }

}

object ComponentLinksConfigExtractor {

  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._
  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._

  type ComponentLinksConfig = List[ComponentLinkConfig]

  private val ComponentsLinkNamespace = "componentLinks"

  implicit val optionListReader: ValueReader[Option[ComponentLinksConfig]] = (config: Config, path: String) =>
    OptionReader
      .optionValueReader[List[Config]]
      .read(config, path)
      .map(_.map(_.as[ComponentLinkConfig]))

  def extract(config: Config): ComponentLinksConfig =
    config
      .as[Option[ComponentLinksConfig]](ComponentsLinkNamespace)
      .getOrElse(List.empty)

}
