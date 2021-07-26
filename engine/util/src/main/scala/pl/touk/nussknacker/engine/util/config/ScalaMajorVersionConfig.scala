package pl.touk.nussknacker.engine.util.config

import java.util.Collections

import com.typesafe.config.{Config, ConfigFactory}

object ScalaMajorVersionConfig {

  val scalaMajorVersion: String = util.Properties.versionNumberString.replaceAll("(\\d+\\.\\d+)\\..*$", "$1")

  def configWithScalaMajorVersion(config: Config): Config = {
    val withMajor = ConfigFactory.parseMap(Collections.singletonMap("scala.major.version", scalaMajorVersion))
    ConfigFactory.load(config.withFallback(withMajor))

  }

}
