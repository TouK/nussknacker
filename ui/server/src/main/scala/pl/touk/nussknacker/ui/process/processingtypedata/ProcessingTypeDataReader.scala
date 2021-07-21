package pl.touk.nussknacker.ui.process.processingtypedata

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.engine.{DeploymentManagerProvider, ProcessingTypeConfig, ProcessingTypeData}

object ProcessingTypeDataReader extends LazyLogging {

  def loadProcessingTypeData(config: Config): ProcessingTypeDataProvider[ProcessingTypeData] = {
    val types: Map[ProcessingType, ProcessingTypeConfig] = ProcessingTypeDataConfigurationReader.readProcessingTypeConfig(config)
    val valueMap = types.map {
      case (name, typeConfig) =>
        logger.debug(s"Creating scenario manager: $name with config: $typeConfig")
        val managerProvider = ScalaServiceLoader.loadNamed[DeploymentManagerProvider](typeConfig.engineType)
        name -> ProcessingTypeData.createProcessingTypeData(managerProvider, typeConfig)
    }
    new MapBasedProcessingTypeDataProvider[ProcessingTypeData](valueMap)
  }
}
