package pl.touk.nussknacker.engine.util.config

import com.typesafe.config.{Config, ConfigFactory}

import java.io.File
import java.net.URI
import scala.util.Try

object ConfigFactoryExt extends URIExtensions {

  def parseUri(uri: URI, classLoader: ClassLoader): Config = {
    uri.getScheme match {
      // When we migrate to Java 9+ we can use SPI to load a URLStreamHandlerProvider
      // instance to handle the classpath scheme.
      case "classpath" => ConfigFactory.parseResources(classLoader, uri.getSchemeSpecificPart)
      case _ => ConfigFactory.parseURL(uri.withFileSchemeDefault.toURL)
    }
  }

  def parseConfigFallbackChain(resources: List[URI], classLoader: ClassLoader): Config =
    resources
      .map(ConfigFactoryExt.parseUri(_, classLoader))
      .reverse
      .foldLeft(ConfigFactory.empty())(_.withFallback(_))

  private val configLocationProperty: String = "nussknacker.config.locations"

  def parseUnresolved(locationString: String = System.getProperty(configLocationProperty), classLoader: ClassLoader): Config = {
    val locations = for {
      property <- Option(locationString).toList
      singleElement <- property.split(",")
      trimmed = singleElement.trim
    } yield Try(URI.create(trimmed)).filter(_.getScheme.nonEmpty).getOrElse(new File(trimmed).toURI)
    ConfigFactoryExt.parseConfigFallbackChain(locations, classLoader)
  }

}
