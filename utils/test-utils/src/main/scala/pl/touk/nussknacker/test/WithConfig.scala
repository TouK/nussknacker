package pl.touk.nussknacker.test

import com.typesafe.config.{Config, ConfigFactory}

trait WithConfig {

  protected lazy val config: Config = resolveConfig(ConfigFactory.load())

  protected def resolveConfig(config: Config): Config = config
}
