package pl.touk.nussknacker.ui.config

import com.typesafe.config.{Config, ConfigFactory}

/**
  * This class handles two parts of ui config loading:
  * 1. Parsing of "base" config passed via nussknacker.config.locations system property (without resolution)
  * 2. Loading this parsed config with fallback to config inside defaultUiConfig.conf resource
  * This process is split that way to make possible using "base" configs prepared programmatically -
  * see LocalNussknackerWithSingleModel for a sample of such usage
  */
object UiConfigLoader {

  private val defaultConfigResource = "defaultUiConfig.conf"

  def load(baseUnresolvedConfig: Config, classLoader: ClassLoader): Config = {
    val parsedDefaultUiConfig = ConfigFactory.parseResources(defaultConfigResource)
    ConfigFactory.load(classLoader, baseUnresolvedConfig.withFallback(parsedDefaultUiConfig))
  }

}
