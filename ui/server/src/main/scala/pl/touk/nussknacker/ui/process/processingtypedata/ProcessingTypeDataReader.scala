package pl.touk.nussknacker.ui.process.processingtypedata

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.engine.{ProcessManagerProvider, ProcessingTypeConfig, ProcessingTypeData}

object ProcessingTypeDataReader extends LazyLogging {

  import scala.collection.JavaConverters._

  def loadProcessingTypeData(config: Config): ProcessingTypeDataProvider[ProcessingTypeData] = {
    val types: Map[ProcessingType, ProcessingTypeConfig] = readProcessingTypeConfig(config)
    val valueMap = types.map {
      case (name, typeConfig) =>
        logger.debug(s"Creating process manager: $name with config: $typeConfig")
        val managerProvider = ScalaServiceLoader.loadNamed[ProcessManagerProvider](typeConfig.engineType)
        name -> ProcessingTypeData.createProcessingTypeData(managerProvider, typeConfig)
    }
    new MapBasedProcessingTypeDataProvider[ProcessingTypeData](valueMap)
  }

  private def readProcessingTypeConfig(config: Config): Map[String, ProcessingTypeConfig] = {
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
        logger.info("ScenarioTypes configuration is missing - falling back to old configuration")
        processTypes
      case (None, None) => throw new RuntimeException("No scenario types configuration provided")
    }
  }
}
