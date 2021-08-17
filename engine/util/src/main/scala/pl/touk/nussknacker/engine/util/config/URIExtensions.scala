package pl.touk.nussknacker.engine.util.config

import java.io.File
import java.net.URI

trait URIExtensions {
  implicit class ExtendedURI(uri: URI) {
    def withFileSchemeDefault: URI = if (uri.isAbsolute) uri else new File(uri.getSchemeSpecificPart).toURI
  }
}