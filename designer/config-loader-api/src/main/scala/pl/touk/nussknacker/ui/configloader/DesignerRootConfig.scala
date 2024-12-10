package pl.touk.nussknacker.ui.configloader

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion

// TODO: We should extract a class for all configuration options that should be available to designer instead of returning raw hocon config.
//       Thanks to that it will be easier to split processing type config from rest of configs and use this interface programmatically
final case class DesignerRootConfig(rawConfig: ConfigWithUnresolvedVersion)

object DesignerRootConfig {

  def from(config: Config): DesignerRootConfig = {
    DesignerRootConfig(ConfigWithUnresolvedVersion(config))
  }

}
