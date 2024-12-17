package pl.touk.nussknacker.ui.config

import cats.effect.IO
import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion
import pl.touk.nussknacker.engine.util.UriUtils
import pl.touk.nussknacker.engine.util.config.ConfigFactoryExt
import pl.touk.nussknacker.ui.configloader.DesignerConfig

trait DesignerConfigLoader {

  def loadDesignerConfig(): IO[DesignerConfig]

}

/**
  * This class handles two parts of ui config loading:
  * 1. Parsing of "base" config passed via nussknacker.config.locations system property (without resolution)
  * 2. Loading this parsed config with fallback to config inside defaultDesignerConfig.conf resource
  * This process is split that way to make possible using "base" configs prepared programmatically -
  * see LocalNussknackerWithSingleModel for a sample of such usage
  * Result of config loading still keep version with unresolved env variables for purpose of config loading on model side - see
  * InputConfigDuringExecution and ModelConfigLoader
  */
class AlwaysLoadingFileBasedDesignerConfigLoader(classLoader: ClassLoader) extends DesignerConfigLoader {

  private val configLocationsProperty: String = "nussknacker.config.locations"

  private val defaultConfigResource = "defaultDesignerConfig.conf"

  override def loadDesignerConfig(): IO[DesignerConfig] = {
    val locationsPropertyValueOpt = Option(System.getProperty(configLocationsProperty))
    val locations                 = locationsPropertyValueOpt.map(UriUtils.extractListOfLocations).getOrElse(List.empty)
    for {
      baseUnresolvedConfig  <- IO.blocking(new ConfigFactoryExt(classLoader).parseUnresolved(locations))
      parsedDefaultUiConfig <- IO.blocking(ConfigFactory.parseResources(classLoader, defaultConfigResource))
      unresolvedConfigWithFallbackToDefaults = baseUnresolvedConfig.withFallback(parsedDefaultUiConfig)
    } yield DesignerConfig(ConfigWithUnresolvedVersion(classLoader, unresolvedConfigWithFallbackToDefaults))
  }

}

/**
 * This implementation is more straightforward - it only parse config without any property checking and fallbacks
 */
class SimpleConfigLoadingDesignerConfigLoader(loadConfig: => Config) extends DesignerConfigLoader {

  override def loadDesignerConfig(): IO[DesignerConfig] = IO.delay(DesignerConfig.from(loadConfig))

}

object DesignerConfigLoader {

  def apply(classLoader: ClassLoader): AlwaysLoadingFileBasedDesignerConfigLoader =
    new AlwaysLoadingFileBasedDesignerConfigLoader(classLoader)

  def fromConfig(loadConfig: => Config): SimpleConfigLoadingDesignerConfigLoader =
    new SimpleConfigLoadingDesignerConfigLoader(loadConfig)

}
