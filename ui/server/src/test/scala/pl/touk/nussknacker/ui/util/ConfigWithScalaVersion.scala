package pl.touk.nussknacker.ui.util


import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.util.config.ScalaBinaryConfig

object ConfigWithScalaVersion {

  val config: Config = ScalaBinaryConfig.configWithScalaBinaryVersion(ConfigFactory.parseResources("ui.conf"))
}
