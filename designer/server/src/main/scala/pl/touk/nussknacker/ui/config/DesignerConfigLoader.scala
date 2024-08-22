package pl.touk.nussknacker.ui.config

import cats.effect.IO
import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion
import pl.touk.nussknacker.engine.util.config.ConfigFactoryExt

/**
  * This class handles two parts of ui config loading:
  * 1. Parsing of "base" config passed via nussknacker.config.locations system property (without resolution)
  * 2. Loading this parsed config with fallback to config inside defaultDesignerConfig.conf resource
  * This process is split that way to make possible using "base" configs prepared programmatically -
  * see LocalNussknackerWithSingleModel for a sample of such usage
  * Result of config loading still keep version with unresolved env variables for purpose of config loading on model side - see
  * InputConfigDuringExecution and ModelConfigLoader
  */
object DesignerConfigLoader {

  private val defaultConfigResource = "defaultDesignerConfig.conf"

  def load(classLoader: ClassLoader): IO[ConfigWithUnresolvedVersion] = {
    for {
      baseConfig   <- IO.blocking(ConfigFactoryExt.parseUnresolved(classLoader = classLoader))
      loadedConfig <- load(baseConfig, classLoader)
    } yield loadedConfig
  }

  def load(baseUnresolvedConfig: Config, classLoader: ClassLoader): IO[ConfigWithUnresolvedVersion] = {
    IO.blocking {
      val parsedDefaultUiConfig                  = ConfigFactory.parseResources(classLoader, defaultConfigResource)
      val unresolvedConfigWithFallbackToDefaults = baseUnresolvedConfig.withFallback(parsedDefaultUiConfig)
      ConfigWithUnresolvedVersion(classLoader, unresolvedConfigWithFallbackToDefaults)
    }
  }

  def from(config: Config): ConfigWithUnresolvedVersion = {
    ConfigWithUnresolvedVersion(this.getClass.getClassLoader, config)
  }

}
