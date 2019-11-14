package pl.touk.nussknacker.engine.util.config

import java.util.Collections

import com.typesafe.config.{Config, ConfigFactory}

object ScalaMajorVersionConfig {

  val scalaMajorVersion: String = util.Properties.versionNumberString.replaceAll("(\\d+\\.\\d+)\\..*$", "$1")

  def configWithScalaMajorVersion(config: Config = ConfigFactory.defaultApplication()): Config = {
    val withBinary = ConfigFactory.parseMap(Collections.singletonMap("scala.major.version", scalaMajorVersion))
    ConfigFactory.load(config.withFallback(withBinary))

  }

}
