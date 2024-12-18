package pl.touk.nussknacker.ui.config

import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.{ConfigWithUnresolvedVersion, ProcessingTypeConfig}
import pl.touk.nussknacker.ui.configloader.ProcessingTypeConfigs

// TODO: We should extract a class for all configuration options that should be available to designer instead of returning raw hocon config.
//       Thanks to that it will be easier to split processing type config from rest of configs and use this interface programmatically
final case class DesignerConfig private (rawConfig: ConfigWithUnresolvedVersion) {

  import net.ceedubs.ficus.Ficus._

  def processingTypeConfigs: ProcessingTypeConfigs =
    ProcessingTypeConfigs(
      rawConfig
        .readMap("scenarioTypes")
        .getOrElse {
          throw new RuntimeException("No scenario types configuration provided")
        }
        .mapValuesNow(ProcessingTypeConfig.read)
    )

  def configLoaderConfig: Config = rawConfig.resolved.getAs[Config]("configLoader").getOrElse(ConfigFactory.empty())

}

object DesignerConfig {

  def from(config: Config): DesignerConfig = {
    DesignerConfig(ConfigWithUnresolvedVersion(config))
  }

}
