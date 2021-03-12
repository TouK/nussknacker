package pl.touk.nussknacker.engine.util.config

import java.io.File
import java.net.{URI, URL, URLConnection, URLStreamHandler, URLStreamHandlerFactory}
import com.typesafe.config.Config
import net.ceedubs.ficus.{FicusConfig, SimpleFicusConfig}
import net.ceedubs.ficus.readers._

import scala.language.implicitConversions
import scala.util.Try

// We exclude URIReaders with URLReader because of own implementations with fallback to File url/uri
object CustomFicusInstances extends AnyValReaders with StringReader with SymbolReader with OptionReader
  with CollectionReaders with ConfigReader with DurationReaders
  with TryReader with ConfigValueReader with BigNumberReaders
  with ISOZonedDateTimeReader with PeriodReader with LocalDateReader
  with InetSocketAddressReaders {

  implicit val uriValueReader: ValueReader[URI] = ValueReader[String]
    .map(value => Try(new URI(value)).filter(Option(_).map(_.getScheme).exists(_.nonEmpty)).getOrElse(new File(value).toURI))

  implicit val urlValueReader: ValueReader[URL] = uriValueReader.map(_.toURL)

  implicit def toFicusConfig(config: Config): FicusConfig = SimpleFicusConfig(config)

}
