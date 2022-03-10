package pl.touk.nussknacker.engine

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.ValueReader

import java.io.File
import java.net.URL

case class ProcessingTypeConfig(engineType: String,
                                classPath: List[URL],
                                deploymentConfig: Config,
                                modelConfig: Config)

object ProcessingTypeConfig {

  import net.ceedubs.ficus.Ficus.toFicusConfig
  import net.ceedubs.ficus.readers.URIReaders._
  import net.ceedubs.ficus.readers.CollectionReaders._

  private implicit val urlValueReader: ValueReader[URL] =
    javaURIReader.map { uri =>
      (if (uri.isAbsolute) uri else new File(uri.getSchemeSpecificPart).toURI).toURL
    }

  implicit val reader: ValueReader[ProcessingTypeConfig] = ValueReader.relative(read)

  def read(config: Config): ProcessingTypeConfig =
    ProcessingTypeConfig(
      config.getString("deploymentConfig.type"),
      config.as[List[URL]]("modelConfig.classPath"),
      config.getConfig("deploymentConfig"),
      config.getConfig("modelConfig")
    )
}