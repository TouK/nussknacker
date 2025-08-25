package pl.touk.nussknacker.test

import com.typesafe.config.{Config, ConfigFactory}

trait WithModelConfig {

  protected lazy val modelConfig: Config =
    resolveModelConfig(ConfigFactory.empty())

  protected def resolveModelConfig(config: Config): Config = config
}
