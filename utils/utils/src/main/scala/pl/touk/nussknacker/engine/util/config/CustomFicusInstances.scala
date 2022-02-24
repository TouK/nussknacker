package pl.touk.nussknacker.engine.util.config

import java.net.URL
import com.typesafe.config.Config
import net.ceedubs.ficus.{FicusConfig, SimpleFicusConfig}
import net.ceedubs.ficus.readers._

import java.util.UUID
import scala.language.implicitConversions


// We exclude URLReader because of our own implementation with fallback to a File url
object CustomFicusInstances extends AnyValReaders with StringReader with SymbolReader with OptionReader
  with CollectionReaders with ConfigReader with DurationReaders
  with TryReader with ConfigValueReader with BigNumberReaders
  with ISOZonedDateTimeReader with PeriodReader with LocalDateReader
  with InetSocketAddressReaders with URIReaders with URIExtensions {

  implicit val urlValueReader: ValueReader[URL] =
    javaURIReader.map(uri => uri.withFileSchemeDefault.toURL)

  implicit val uuidValueReader: ValueReader[UUID] = ValueReader[String]
    .map(value => UUID.fromString(value))

  implicit def toFicusConfig(config: Config): FicusConfig = SimpleFicusConfig(config)

}
