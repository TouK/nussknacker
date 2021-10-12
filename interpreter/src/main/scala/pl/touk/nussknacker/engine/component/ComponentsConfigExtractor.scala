package pl.touk.nussknacker.engine.component

import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, SingleComponentConfig}
import com.typesafe.config.Config
import net.ceedubs.ficus.readers.{OptionReader, ValueReader}

/**
  * TODO: It's temporary solution until we migrate to ComponentProvider
  */
object ComponentsConfigExtractor {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.util.config.FicusReaders._

  type ComponentsConfig = Map[String, SingleComponentConfig]

  implicit val componentGroupNameReader: ValueReader[Option[ComponentGroupName]] = (config: Config, path: String) =>
    OptionReader
    .optionValueReader[String]
    .read(config, path)
    .map(ComponentGroupName(_))

  private val ComponentsConfigPath = "componentsConfig"

  def extract(config: Config): ComponentsConfig =
    config.getOrElse[ComponentsConfig](ComponentsConfigPath, Map.empty)
}
