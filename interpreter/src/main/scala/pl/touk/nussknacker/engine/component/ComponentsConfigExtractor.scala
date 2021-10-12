package pl.touk.nussknacker.engine.component

import pl.touk.nussknacker.engine.api.component.SingleComponentConfig
import com.typesafe.config.Config

/**
  * TODO: It's temporary solution until we migrate to ComponentProvider
  */
object ComponentsConfigExtractor {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.util.config.FicusReaders._

  type ComponentsConfig = Map[String, SingleComponentConfig]

  private val ComponentsConfigPath = "componentsConfig"

  def extract(config: Config): ComponentsConfig =
    config.getOrElse[ComponentsConfig](ComponentsConfigPath, Map.empty)
}
