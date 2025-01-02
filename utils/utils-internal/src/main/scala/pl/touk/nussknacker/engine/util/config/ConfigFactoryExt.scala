package pl.touk.nussknacker.engine.util.config

import com.typesafe.config.{Config, ConfigFactory}

import java.net.URI

class ConfigFactoryExt(classLoader: ClassLoader) extends URIExtensions {

  def parseUnresolved(locations: List[URI]): Config =
    locations
      .map(parseUri)
      .reverse
      .foldLeft(ConfigFactory.empty())(_.withFallback(_))

  def parseUri(uri: URI): Config = {
    uri.getScheme match {
      // When we migrate to Java 9+ we can use SPI to load a URLStreamHandlerProvider
      // instance to handle the classpath scheme.
      case "classpath" => ConfigFactory.parseResources(classLoader, uri.getSchemeSpecificPart)
      case _           => ConfigFactory.parseURL(uri.withFileSchemeDefault.toURL)
    }
  }

}
