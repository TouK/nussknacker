package pl.touk.nussknacker.ui.util


import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig

object ConfigWithScalaVersion {

  val config: Config = ScalaMajorVersionConfig.configWithScalaMajorVersion(ConfigFactory.parseResources("ui.conf"))

  val streamingProcessTypeConfig: Config = config.getConfig("scenarioTypes.streaming")
}
