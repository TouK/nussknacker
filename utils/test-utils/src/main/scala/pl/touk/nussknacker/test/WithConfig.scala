package pl.touk.nussknacker.test

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.commons.io.IOUtils

import java.nio.charset.StandardCharsets

trait WithConfig {

  // Here is the place where we can change the entry config file
  protected val configFilename: Option[String] = None

  protected lazy val config: Config = {
    val config = configFilename
      .map(file => IOUtils.resourceToString(s"/$file", StandardCharsets.UTF_8))
      .map(ConfigFactory.parseString)
      .getOrElse(ConfigFactory.empty())

    resolveConfig(config.resolve())
  }

  protected def resolveConfig(config: Config): Config = config
}
