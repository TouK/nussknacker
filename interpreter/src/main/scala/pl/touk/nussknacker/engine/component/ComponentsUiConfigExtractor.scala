package pl.touk.nussknacker.engine.component

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.{OptionReader, ValueReader}
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ComponentId, SingleComponentConfig}

/**
  * TODO: It's temporary solution until we migrate to ComponentProvider
  */
object ComponentsUiConfigExtractor {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.api.config.ComponentFicusReaders._

  type ComponentsUiConfig = Map[String, SingleComponentConfig]

  private implicit val componentsUiGroupNameReader: ValueReader[Option[ComponentGroupName]] = (config: Config, path: String) =>
    OptionReader
    .optionValueReader[String]
    .read(config, path)
    .map(ComponentGroupName(_))

  private implicit val componentsUiComponentIdReader: ValueReader[Option[ComponentId]] = (config: Config, path: String) =>
    OptionReader
      .optionValueReader[String]
      .read(config, path)
      .map(ComponentId.apply)

  private val ComponentsUiConfigPath = "componentsUiConfig"

  def extract(config: Config): ComponentsUiConfig =
    config.getOrElse[ComponentsUiConfig](ComponentsUiConfigPath, Map.empty)
}
