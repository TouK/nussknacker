package pl.touk.nussknacker.engine

import _root_.sttp.client3.SttpBackend
import akka.actor.ActorSystem
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, ProcessingTypeDeploymentService}
import pl.touk.nussknacker.ui.statistics.ProcessingTypeUsageStatistics

import scala.concurrent.{ExecutionContext, Future}

case class ProcessingTypeData(deploymentManager: DeploymentManager,
                              modelData: ModelData,
                              typeSpecificInitialData: TypeSpecificInitialData,
                              additionalPropertiesConfig: Map[String, AdditionalPropertyConfig],
                              additionalValidators: List[CustomProcessValidator],
                              usageStatistics: ProcessingTypeUsageStatistics) extends AutoCloseable {

  def close(): Unit = {
    modelData.close()
    deploymentManager.close()
  }

}

object ProcessingTypeData {

  def createProcessingTypeData(deploymentManagerProvider: DeploymentManagerProvider, modelData: ModelData, managerConfig: Config)
                              (implicit ec: ExecutionContext, actorSystem: ActorSystem,
                               sttpBackend: SttpBackend[Future, Any],
                               deploymentService: ProcessingTypeDeploymentService): ProcessingTypeData = {
    val manager = deploymentManagerProvider.createDeploymentManager(modelData, managerConfig)
    import net.ceedubs.ficus.Ficus._
    import pl.touk.nussknacker.engine.util.config.FicusReaders._
    val additionalProperties =
      deploymentManagerProvider.additionalPropertiesConfig(managerConfig) ++ modelData.processConfig.getOrElse[Map[String, AdditionalPropertyConfig]]("additionalPropertiesConfig", Map.empty)

    ProcessingTypeData(
      manager,
      modelData,
      deploymentManagerProvider.typeSpecificInitialData(managerConfig),
      additionalProperties,
      deploymentManagerProvider.additionalValidators(managerConfig) ,
      ProcessingTypeUsageStatistics(managerConfig))
  }

  def createProcessingTypeData(deploymentManagerProvider: DeploymentManagerProvider, processTypeConfig: ProcessingTypeConfig)
                              (implicit ec: ExecutionContext, actorSystem: ActorSystem,
                               sttpBackend: SttpBackend[Future, Any],
                               deploymentService: ProcessingTypeDeploymentService): ProcessingTypeData = {
    val managerConfig = processTypeConfig.deploymentConfig
    createProcessingTypeData(deploymentManagerProvider, ModelData(processTypeConfig), managerConfig)
  }

}


