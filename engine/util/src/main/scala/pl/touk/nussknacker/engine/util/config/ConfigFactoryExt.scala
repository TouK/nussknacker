package pl.touk.nussknacker.engine.util.config

import com.typesafe.config.{Config, ConfigFactory}

import java.io.File
import java.net.URI

object ConfigFactoryExt {
  def parseUri(uri: URI): Config = {
    uri.getScheme match {
      // When we migrate to Java 9+ we can use SPI to load a URLStreamHandlerProvider
      // instance to handle the classpath scheme.
      case "classpath" => ConfigFactory.parseResources(uri.getSchemeSpecificPart)
      case _ => ConfigFactory.parseURL(uri.toURL)
    }
  }
}
