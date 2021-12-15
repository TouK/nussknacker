package pl.touk.nussknacker.ui.util


import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.restmodel.process.ProcessingType

object ConfigWithScalaVersion {

  val config: Config = ScalaMajorVersionConfig.configWithScalaMajorVersion(ConfigFactory.parseResources("ui.conf"))

  val streamingProcessingType: ProcessingType = "streaming"

  val streamingProcessTypeConfig: Config = config.getConfig(s"scenarioTypes.$streamingProcessingType")
}
