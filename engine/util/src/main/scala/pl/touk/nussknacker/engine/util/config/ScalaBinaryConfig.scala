package pl.touk.nussknacker.engine.util.config

import java.util.Collections

import com.typesafe.config.{Config, ConfigFactory}

object ScalaBinaryConfig {

  val scalaBinaryVersion: String = util.Properties.versionNumberString.replaceAll("(\\d+\\.\\d+)\\..*$", "$1")

  def configWithScalaBinaryVersion(config: Config = ConfigFactory.defaultApplication()): Config = {
    val withBinary = ConfigFactory.parseMap(Collections.singletonMap("scala.binary.version", scalaBinaryVersion))
    ConfigFactory.load(config.withFallback(withBinary))

  }

}
