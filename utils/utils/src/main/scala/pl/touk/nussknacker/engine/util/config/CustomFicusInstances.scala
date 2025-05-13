package pl.touk.nussknacker.engine.util.config

import com.typesafe.config.Config
import net.ceedubs.ficus.{FicusConfig, SimpleFicusConfig}
import net.ceedubs.ficus.readers._

import java.net.URL
import java.util.UUID
import scala.language.implicitConversions

/**
 * Configuration extending default Ficus decoding.
 *
 * Customizations:
 *
 *  - [[URLReader]] is modified to default to the `file://` scheme
 *  - String values can be read as [[UUID]] instances
 */
trait CustomFicusInstances
    extends AnyValReaders
    with StringReader
    with SymbolReader
    with OptionReader
    with CollectionReaders
    with ConfigReader
    with DurationReaders
    with TryReader
    with ConfigValueReader
    with BigNumberReaders
    with ISOZonedDateTimeReader
    with PeriodReader
    with LocalDateReader
    with InetSocketAddressReaders
    with URIReaders
    with URIExtensions {

  implicit val urlValueReader: ValueReader[URL] =
    URIReaders.javaURIReader.map(uri => uri.withFileSchemeDefault.toURL)

  implicit val uuidValueReader: ValueReader[UUID] =
    StringReader.stringValueReader.map(value => UUID.fromString(value))

  implicit def toFicusConfig(config: Config): FicusConfig = SimpleFicusConfig(config)

}

object CustomFicusInstances extends CustomFicusInstances
