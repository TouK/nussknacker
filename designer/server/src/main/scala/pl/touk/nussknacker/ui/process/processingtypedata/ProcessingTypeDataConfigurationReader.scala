package pl.touk.nussknacker.ui.process.processingtypedata

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.{ConfigWithUnresolvedVersion, ProcessingTypeConfig}

object ProcessingTypeDataConfigurationReader extends LazyLogging {

  import scala.jdk.CollectionConverters._

  def readProcessingTypeConfig(config: ConfigWithUnresolvedVersion): Map[String, ProcessingTypeConfig] = {
    val processTypesOption = read(config, "processTypes")
    val scenarioTypesOption = read(config, "scenarioTypes")
    (scenarioTypesOption, processTypesOption) match {
      case (Some(scenarioTypes), _) => scenarioTypes
      case (None, Some(processTypes)) =>
        logger.warn("ScenarioTypes configuration is missing - falling back to old processTypes configuration - processTypes will be removed in next version")
        processTypes
      case (None, None) => throw new RuntimeException("No scenario types configuration provided")
    }
  }

  private def read(config: ConfigWithUnresolvedVersion, path: String): Option[Map[String, ProcessingTypeConfig]] = {
    if (config.resolved.hasPath(path)) {
      val nestedConfig = config.getConfig(path)
      Some(nestedConfig.resolved.root().entrySet().asScala.map(_.getKey).map { key =>
        key -> ProcessingTypeConfig.read(nestedConfig.getConfig(key))
      }.toMap)
    } else {
      None
    }
  }

}
