package pl.touk.nussknacker.test

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.commons.io.IOUtils

import java.nio.charset.StandardCharsets

trait WithConfig {

  protected val configFilename: String = "application.conf"

  protected lazy val config: Config = {
    val resource = IOUtils.resourceToString(s"/$configFilename", StandardCharsets.UTF_8)
    val config   = ConfigFactory.parseString(resource)
    resolveConfig(config.resolve())
  }

  protected def resolveConfig(config: Config): Config = config
}
