package pl.touk.nussknacker.test

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.commons.io.IOUtils

import java.nio.charset.StandardCharsets

import scala.util.Try

trait WithConfig {

  protected val configFilename: String = "application.conf"

  protected lazy val config: Config = {
    val config = Try(IOUtils.resourceToString(s"/$configFilename", StandardCharsets.UTF_8)).toOption
      .map(ConfigFactory.parseString)
      .getOrElse(ConfigFactory.empty())

    resolveConfig(config.resolve())
  }

  protected def resolveConfig(config: Config): Config = config
}
