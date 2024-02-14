package pl.touk.nussknacker.ui.process.processingtype

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.{ConfigWithUnresolvedVersion, ProcessingTypeConfig}

object ProcessingTypeDataConfigurationReader extends LazyLogging {

  import scala.jdk.CollectionConverters._

  def readProcessingTypeConfig(config: ConfigWithUnresolvedVersion): Map[String, ProcessingTypeConfig] = {
    read(config, "scenarioTypes").getOrElse {
      throw new RuntimeException("No scenario types configuration provided")
    }
  }

  private def read(config: ConfigWithUnresolvedVersion, path: String): Option[Map[String, ProcessingTypeConfig]] = {
    if (config.resolved.hasPath(path)) {
      val nestedConfig = config.getConfig(path)
      Some(
        nestedConfig.resolved
          .root()
          .entrySet()
          .asScala
          .map(_.getKey)
          .map { key =>
            key -> ProcessingTypeConfig.read(nestedConfig.getConfig(key))
          }
          .toMap
      )
    } else {
      None
    }
  }

}
