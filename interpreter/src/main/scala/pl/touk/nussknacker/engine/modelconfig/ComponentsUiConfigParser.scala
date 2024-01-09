package pl.touk.nussknacker.engine.modelconfig

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.{OptionReader, ValueReader}
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ComponentId, ComponentInfo, SingleComponentConfig}

/**
  * TODO: It's temporary solution until we migrate to ComponentProvider
  */
object ComponentsUiConfigParser {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.util.config.FicusReaders._

  private implicit val componentsUiGroupNameReader: ValueReader[Option[ComponentGroupName]] =
    (config: Config, path: String) =>
      OptionReader
        .optionValueReader[String]
        .read(config, path)
        .map(ComponentGroupName(_))

  private implicit val componentsUiComponentIdReader: ValueReader[Option[ComponentId]] =
    (config: Config, path: String) =>
      OptionReader
        .optionValueReader[String]
        .read(config, path)
        .map(ComponentId.apply)

  private val ComponentsUiConfigPath = "componentsUiConfig"

  def parse(config: Config): ComponentsUiConfig = {
    ComponentsUiConfig(config.getOrElse[Map[String, SingleComponentConfig]](ComponentsUiConfigPath, Map.empty))
  }

}

case class ComponentsUiConfig(config: Map[String, SingleComponentConfig]) {

  def getConfig(info: ComponentInfo): SingleComponentConfig = {
    config
      .get(info.toString)
      // Should we still support lookup by name?
      .orElse(config.get(info.name))
      .getOrElse(SingleComponentConfig.zero)
  }

}

object ComponentsUiConfig {

  val Empty: ComponentsUiConfig = ComponentsUiConfig(Map.empty)

}
