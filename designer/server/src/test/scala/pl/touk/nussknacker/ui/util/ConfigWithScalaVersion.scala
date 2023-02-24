package pl.touk.nussknacker.ui.util

import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes

object ConfigWithScalaVersion {

  val TestsConfig: Config = ScalaMajorVersionConfig.configWithScalaMajorVersion(ConfigFactory.parseResources("designer.conf"))

  val StreamingProcessTypeConfig: ConfigWithUnresolvedVersion =
    ConfigWithUnresolvedVersion(TestsConfig.getConfig(s"scenarioTypes.${TestProcessingTypes.Streaming}"))

}
