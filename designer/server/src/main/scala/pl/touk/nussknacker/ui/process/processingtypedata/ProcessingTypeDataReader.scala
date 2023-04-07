package pl.touk.nussknacker.ui.process.processingtypedata

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.ProcessingTypeDeploymentService
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.engine.{CombinedProcessingTypeData, ConfigWithUnresolvedVersion, DeploymentManagerProvider, ProcessingTypeConfig, ProcessingTypeData}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.process.deployment.DeploymentService
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

object ProcessingTypeDataReader extends ProcessingTypeDataReader

trait ProcessingTypeDataReader extends LazyLogging {


  def loadProcessingTypeData(config: ConfigWithUnresolvedVersion)(implicit ec: ExecutionContext, actorSystem: ActorSystem,
                                                                  sttpBackend: SttpBackend[Future, Any],
                                                                  deploymentService: DeploymentService,
                                                                  categoryService: ProcessCategoryService): ProcessingTypeDataProvider[ProcessingTypeData, CombinedProcessingTypeData] = {
    val types: Map[ProcessingType, ProcessingTypeConfig] = ProcessingTypeDataConfigurationReader.readProcessingTypeConfig(config)
    val valueMap = types
      .filterKeysNow(categoryService.getProcessingTypeCategories(_).nonEmpty)
      .map {
        case (name, typeConfig) =>
          name -> createProcessingTypeData(name, typeConfig)
      }

    // Here all processing types are loaded and we are ready to perform additional configuration validations
    // to assert the loaded configuration is correct (fail-fast approach).
    val combinedData = createCombinedData(valueMap, categoryService)

    new MapBasedProcessingTypeDataProvider[ProcessingTypeData, CombinedProcessingTypeData](valueMap, combinedData)
  }

  protected def createProcessingTypeData(name: ProcessingType, typeConfig: ProcessingTypeConfig)
                                        (implicit ec: ExecutionContext, actorSystem: ActorSystem,
                                         sttpBackend: SttpBackend[Future, Any],
                                         deploymentService: DeploymentService): ProcessingTypeData = {
    logger.debug(s"Creating scenario manager: $name with config: $typeConfig")
    val managerProvider = ScalaServiceLoader.loadNamed[DeploymentManagerProvider](typeConfig.engineType)
    implicit val processTypeDeploymentService: ProcessingTypeDeploymentService = new DefaultProcessingTypeDeploymentService(name, deploymentService)
    ProcessingTypeData.createProcessingTypeData(managerProvider, typeConfig)
  }

  protected def createCombinedData(valueMap: Map[ProcessingType, ProcessingTypeData], categoryService: ProcessCategoryService): CombinedProcessingTypeData = {
    CombinedProcessingTypeData.create(valueMap, categoryService)
  }

}
