package pl.touk.nussknacker.engine.util.config

import com.typesafe.config.{Config, ConfigFactory}

import java.net.URI

object ConfigFactoryExt {

  def parseUri(uri: URI, classLoader: ClassLoader): Config = {
    uri.getScheme match {
      // When we migrate to Java 9+ we can use SPI to load a URLStreamHandlerProvider
      // instance to handle the classpath scheme.
      case "classpath" => ConfigFactory.parseResources(classLoader, uri.getSchemeSpecificPart)
      case _ => ConfigFactory.parseURL(uri.toURL)
    }
  }

  def parseConfigFallbackChain(resources: List[URI], classLoader: ClassLoader): Config =
    resources
      .map(ConfigFactoryExt.parseUri(_, classLoader))
      .reverse
      .foldLeft(ConfigFactory.empty())(_.withFallback(_))
}
