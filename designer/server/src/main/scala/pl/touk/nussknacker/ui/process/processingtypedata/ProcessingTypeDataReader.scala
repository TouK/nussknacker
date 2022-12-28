package pl.touk.nussknacker.ui.process.processingtypedata

import akka.actor.ActorSystem
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.ProcessingTypeDeploymentService
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.engine.{DeploymentManagerProvider, ProcessingTypeConfig, ProcessingTypeData}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.process.deployment.DeploymentService
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

object ProcessingTypeDataReader extends ProcessingTypeDataReader

trait ProcessingTypeDataReader extends LazyLogging {

  def loadProcessingTypeData(config: Config)(implicit ec: ExecutionContext, actorSystem: ActorSystem,
                                             sttpBackend: SttpBackend[Future, Nothing, NothingT],
                                             deploymentService: DeploymentService,
                                             categoriesService: ProcessCategoryService): ProcessingTypeDataProvider[ProcessingTypeData] = {
    val types: Map[ProcessingType, ProcessingTypeConfig] = ProcessingTypeDataConfigurationReader.readProcessingTypeConfig(config)
    val valueMap = types
      .filterKeysNow(categoriesService.getProcessingTypeCategories(_).nonEmpty)
      .map {
        case (name, typeConfig) =>
          name -> createProcessingTypeData(name, typeConfig)
      }
    new MapBasedProcessingTypeDataProvider[ProcessingTypeData](valueMap)
  }

  protected def createProcessingTypeData(name: ProcessingType, typeConfig: ProcessingTypeConfig)
                                        (implicit ec: ExecutionContext, actorSystem: ActorSystem,
                                         sttpBackend: SttpBackend[Future, Nothing, NothingT],
                                         deploymentService: DeploymentService): ProcessingTypeData = {
    logger.debug(s"Creating scenario manager: $name with config: $typeConfig")
    val managerProvider = ScalaServiceLoader.loadNamed[DeploymentManagerProvider](typeConfig.engineType)
    implicit val processTypeDeploymentService: ProcessingTypeDeploymentService = new DefaultProcessingTypeDeploymentService(name, deploymentService)
    ProcessingTypeData.createProcessingTypeData(managerProvider, typeConfig)
  }
}