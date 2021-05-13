package pl.touk.nussknacker.ui.config

import com.typesafe.config.{Config, ConfigFactory}

//
object ConfigWithDefaults {

  private val defaultConfigResource = "defaultUiConfig.conf"

  def apply(base: Config): Config = base.withFallback(ConfigFactory.load(defaultConfigResource)).resolve()


}
