package pl.touk.nussknacker.engine

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.ValueReader

import java.io.File
import java.net.URL

case class ProcessingTypeConfig(
    deploymentManagerType: String,
    classPath: List[URL],
    deploymentConfig: Config,
    modelConfig: ConfigWithUnresolvedVersion,
    category: String
)

object ProcessingTypeConfig {

  import net.ceedubs.ficus.Ficus.{stringValueReader, toFicusConfig}
  import net.ceedubs.ficus.readers.CollectionReaders._
  import net.ceedubs.ficus.readers.OptionReader._
  import net.ceedubs.ficus.readers.URIReaders._

  private implicit val urlValueReader: ValueReader[URL] =
    javaURIReader.map { uri =>
      (if (uri.isAbsolute) uri else new File(uri.getSchemeSpecificPart).toURI).toURL
    }

  def read(config: ConfigWithUnresolvedVersion): ProcessingTypeConfig = {
    ProcessingTypeConfig(
      config.resolved.getString("deploymentConfig.type"),
      config.resolved.as[List[URL]]("modelConfig.classPath"),
      config.resolved.getConfig("deploymentConfig"),
      config.getConfig("modelConfig"),
      config.resolved.as[String]("category")
    )
  }

}
