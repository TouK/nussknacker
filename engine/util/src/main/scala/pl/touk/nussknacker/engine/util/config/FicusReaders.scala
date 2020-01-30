package pl.touk.nussknacker.engine.util.config

import java.io.File
import java.net.{URI, URL}

import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ValueReader

import scala.util.Try

object FicusReaders {

  implicit val urlValueReader: ValueReader[URL] = ValueReader[String]
    .map(value => Try(new URL(value)).getOrElse(new File(value).toURI.toURL))

  implicit val uriValueReader: ValueReader[URI] = ValueReader[String]
    .map(value => Try(new URI(value)).getOrElse(new File(value).toURI))
}
