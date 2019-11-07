package pl.touk.nussknacker.ui.util


import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import pl.touk.nussknacker.engine.util.config.ScalaBinaryConfig

object ConfigWithScalaVersion {

  val scalaBinaryVersion: String = util.Properties.versionNumberString.replaceAll("(\\d+\\.\\d+)\\..*$", "$1")

  val config: Config = ScalaBinaryConfig.configWithScalaBinaryVersion(ConfigFactory.parseResources("ui.conf"))
}
