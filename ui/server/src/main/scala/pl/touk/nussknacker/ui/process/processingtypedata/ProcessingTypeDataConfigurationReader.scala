package pl.touk.nussknacker.ui.process.processingtypedata

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ProcessingTypeConfig
import pl.touk.nussknacker.engine.api.config.LoadedConfig

object ProcessingTypeDataConfigurationReader extends LazyLogging {

  import pl.touk.nussknacker.engine.util.Implicits._

  def readProcessingTypeConfig(config: LoadedConfig): Map[String, ProcessingTypeConfig] = {
    def readProcessingTypeConfigMap(config: LoadedConfig) = {
      config.entries.mapValuesNow(ProcessingTypeConfig.read)
    }

    val processTypesOption = config.getOpt("processTypes").map(readProcessingTypeConfigMap)
    val scenarioTypesOption = config.getOpt("scenarioTypes").map(readProcessingTypeConfigMap)
    (scenarioTypesOption, processTypesOption) match {
      case (Some(scenarioTypes), _) => scenarioTypes
      case (None, Some(processTypes)) =>
        logger.warn("ScenarioTypes configuration is missing - falling back to old processTypes configuration - processTypes will be removed in next version")
        processTypes
      case (None, None) => throw new RuntimeException("No scenario types configuration provided")
    }
  }
}
