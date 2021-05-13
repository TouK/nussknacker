package pl.touk.nussknacker.engine.util.config

import com.typesafe.config.{Config, ConfigFactory}

import java.io.File
import java.net.URI
import scala.util.Try

object ConfigFactoryExt {

  private val configLocationProperty = "config.locations"

  def parseUri(uri: URI): Config = {
    uri.getScheme match {
      // When we migrate to Java 9+ we can use SPI to load a URLStreamHandlerProvider
      // instance to handle the classpath scheme.
      case "classpath" => ConfigFactory.parseResources(uri.getSchemeSpecificPart)
      case _ => ConfigFactory.parseURL(uri.toURL)
    }
  }

  def load(locationString: String = System.getProperty(configLocationProperty)): Config = {
    val locations = for {
      property <- Option(locationString).toList
      singleElement <- property.split(",")
      trimmed = singleElement.trim
    } yield Try(URI.create(trimmed)).filter(_.getScheme.nonEmpty).getOrElse(new File(trimmed).toURI)
    load(locations)
  }

  def load(resources: List[URI]): Config = {
    resources.map(ConfigFactoryExt.parseUri).reverse.foldLeft(ConfigFactory.empty())(_.withFallback(_))
  }

}
