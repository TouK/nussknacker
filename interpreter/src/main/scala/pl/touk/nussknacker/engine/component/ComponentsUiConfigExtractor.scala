package pl.touk.nussknacker.engine.component

import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ComponentId, SingleComponentConfig}
import com.typesafe.config.Config
import net.ceedubs.ficus.readers.{OptionReader, ValueReader}

/**
  * TODO: It's temporary solution until we migrate to ComponentProvider
  */
object ComponentsUiConfigExtractor {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.util.config.FicusReaders._

  type ComponentsUiConfigType = Map[String, SingleComponentConfig]

  implicit class ComponentsUiConfig(config: ComponentsUiConfigType) {
    def getComponentIcon(componentName: String): Option[String] =
      config
        .get(componentName)
        .flatMap(_.icon)

    def getOverriddenComponentId(componentName: String, defaultComponentId: ComponentId): ComponentId =
      getComponentConfig(componentName, defaultComponentId)
        .flatMap(_.componentId)
        .getOrElse(defaultComponentId)

    private def getComponentConfig(componentName: String, defaultComponentId: ComponentId): Option[SingleComponentConfig] = {
      val componentId = config.get(componentName).filterNot(_ == SingleComponentConfig.zero)

      //It's work around for components with the same name and different componentType, eg. kafka-avro
      // where default id is combination of processingType-componentType-name
      val componentIdForDefaultComponentId = config.get(defaultComponentId.value)

      componentId.orElse(componentIdForDefaultComponentId)
    }
  }

  private implicit val componentsUiGroupNameReader: ValueReader[Option[ComponentGroupName]] = (config: Config, path: String) =>
    OptionReader
    .optionValueReader[String]
    .read(config, path)
    .map(ComponentGroupName(_))

  private implicit val componentsUiComponentIdReader: ValueReader[Option[ComponentId]] = (config: Config, path: String) =>
    OptionReader
      .optionValueReader[String]
      .read(config, path)
      .map(ComponentId.create)

  private val ComponentsUiConfigPath = "componentsUiConfig"

  def extract(config: Config): ComponentsUiConfigType =
    config.getOrElse[ComponentsUiConfigType](ComponentsUiConfigPath, Map.empty)
}
