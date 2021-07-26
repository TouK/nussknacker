package pl.touk.nussknacker.ui.config

import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.util.config.ConfigFactoryExt

import java.io.File
import java.net.URI
import scala.util.Try

/**
  * This class handles two parts of ui config loading:
  * 1. Parsing of "base" config passed via nussknacker.config.locations system property (without resolution)
  * 2. Loading this parsed config with fallback to config inside defaultUiConfig.conf resource
  * This process is split that way to make possible using "base" configs prepared programmatically -
  * see LocalNussknackerWithSingleModel for a sample of such usage
  */
object UiConfigLoader {

  private val configLocationProperty: String = "nussknacker.config.locations"

  private val defaultConfigResource = "defaultUiConfig.conf"

  def parseUnresolved(locationString: String = System.getProperty(configLocationProperty), classLoader: ClassLoader): Config = {
    val locations = for {
      property <- Option(locationString).toList
      singleElement <- property.split(",")
      trimmed = singleElement.trim
    } yield Try(URI.create(trimmed)).filter(_.getScheme.nonEmpty).getOrElse(new File(trimmed).toURI)
    parseUnresolved(locations, classLoader)
  }

  def parseUnresolved(resources: List[URI], classLoader: ClassLoader): Config = {
    ConfigFactoryExt.parseConfigFallbackChain(resources, classLoader)
  }

  def load(baseUnresolvedConfig: Config, classLoader: ClassLoader): Config = {
    val parsedDefaultUiConfig = ConfigFactory.parseResources(defaultConfigResource)
    ConfigFactory.load(classLoader, baseUnresolvedConfig.withFallback(parsedDefaultUiConfig))
  }

}
