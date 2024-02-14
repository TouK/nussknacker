package pl.touk.nussknacker.engine

import com.typesafe.config.Config

case class ProcessingTypeConfig(
    deploymentManagerType: String,
    classPath: List[String],
    deploymentConfig: Config,
    modelConfig: ConfigWithUnresolvedVersion,
    category: String
)

object ProcessingTypeConfig {

  import net.ceedubs.ficus.Ficus.{stringValueReader, toFicusConfig}
  import net.ceedubs.ficus.readers.CollectionReaders._

  def read(config: ConfigWithUnresolvedVersion): ProcessingTypeConfig = {
    ProcessingTypeConfig(
      config.resolved.getString("deploymentConfig.type"),
      config.resolved.as[List[String]]("modelConfig.classPath"),
      config.resolved.getConfig("deploymentConfig"),
      config.getConfig("modelConfig"),
      config.resolved.as[String]("category")
    )
  }

}
