package pl.touk.nussknacker.ui.process.processingtypedata

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.engine.ProcessingTypeConfig

object ProcessingTypeDataConfigurationReader extends LazyLogging {

  import scala.collection.JavaConverters._

  def readProcessingTypeConfig(config: Config): Map[String, ProcessingTypeConfig] = {
    implicit val reader: ValueReader[Map[String, ProcessingTypeConfig]] = ValueReader.relative { config =>
      config.root().entrySet().asScala.map(_.getKey).map { key =>
        key -> config.as[ProcessingTypeConfig](key)(ProcessingTypeConfig.reader)
      }.toMap
    }

    val processTypesOption = config.getAs[Map[String, ProcessingTypeConfig]]("processTypes")
    val scenarioTypesOption = config.getAs[Map[String, ProcessingTypeConfig]]("scenarioTypes")
    (scenarioTypesOption, processTypesOption) match {
      case (Some(scenarioTypes), _) => scenarioTypes
      case (None, Some(processTypes)) =>
        logger.warn("ScenarioTypes configuration is missing - falling back to old processTypes configuration - processTypes will be removed in next version")
        processTypes
      case (None, None) => throw new RuntimeException("No scenario types configuration provided")
    }
  }
}
