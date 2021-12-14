package pl.touk.nussknacker.ui.process.processingtypedata

import akka.actor.ActorSystem
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.DeploymentService
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.engine.{DeploymentManagerProvider, ProcessingTypeConfig, ProcessingTypeData}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

object ProcessingTypeDataReader extends LazyLogging {

  def loadProcessingTypeData(config: Config)(implicit ec: ExecutionContext, actorSystem: ActorSystem, sttpBackend: SttpBackend[Future, Nothing, NothingT], deploymentService: DeploymentService): ProcessingTypeDataProvider[ProcessingTypeData] = {
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
