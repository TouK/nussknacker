package pl.touk.nussknacker.engine.util.config

import java.io.File
import java.net.URL

import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ValueReader

import scala.util.Try

object FicusReaders {

  implicit val urlValueReader: ValueReader[URL] = ValueReader[String]
    .map(value => Try(new URL(value)).getOrElse(new File(value).toURI.toURL))
}
