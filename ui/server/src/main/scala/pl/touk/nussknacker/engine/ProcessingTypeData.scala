package pl.touk.nussknacker.engine

import akka.actor.ActorSystem
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, ProcessingTypeDeploymentService}
import pl.touk.nussknacker.engine.api.queryablestate.QueryableClient
import _root_.sttp.client.{NothingT, SttpBackend}
import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig

import scala.concurrent.{ExecutionContext, Future}

case class ProcessingTypeData(deploymentManager: DeploymentManager,
                              modelData: ModelData,
                              typeSpecificInitialData: TypeSpecificInitialData,
                              additionalPropertiesConfig: Map[String, AdditionalPropertyConfig],
                              queryableClient: Option[QueryableClient],
                              supportsSignals: Boolean) extends AutoCloseable {

  def close(): Unit = {
    modelData.close()
    deploymentManager.close()
    queryableClient.foreach(_.close())
  }

}

object ProcessingTypeData {

  def createProcessingTypeData(deploymentManagerProvider: DeploymentManagerProvider, modelData: ModelData, managerConfig: Config)
                              (implicit ec: ExecutionContext, actorSystem: ActorSystem,
                               sttpBackend: SttpBackend[Future, Nothing, NothingT],
                               deploymentService: ProcessingTypeDeploymentService): ProcessingTypeData = {
    val manager = deploymentManagerProvider.createDeploymentManager(modelData, managerConfig)
    val queryableClient = deploymentManagerProvider.createQueryableClient(managerConfig)
    ProcessingTypeData(
      manager,
      modelData,
      deploymentManagerProvider.typeSpecificInitialData(managerConfig),
      prepareAdditionalPropertiesConfig(deploymentManagerProvider, modelData),
      queryableClient,
      deploymentManagerProvider.supportsSignals)
  }

  private def prepareAdditionalPropertiesConfig(deploymentManagerProvider: DeploymentManagerProvider, modelData: ModelData): Map[String, AdditionalPropertyConfig] = {
    import net.ceedubs.ficus.Ficus._
    import pl.touk.nussknacker.engine.util.config.FicusReaders._
    deploymentManagerProvider.additionalPropertiesConfig ++ modelData.processConfig.getOrElse[Map[String, AdditionalPropertyConfig]]("additionalPropertiesConfig", Map.empty)
  }

  def createProcessingTypeData(deploymentManagerProvider: DeploymentManagerProvider, processTypeConfig: ProcessingTypeConfig)
                              (implicit ec: ExecutionContext, actorSystem: ActorSystem,
                               sttpBackend: SttpBackend[Future, Nothing, NothingT],
                               deploymentService: ProcessingTypeDeploymentService): ProcessingTypeData = {
    val managerConfig = processTypeConfig.deploymentConfig
    createProcessingTypeData(deploymentManagerProvider, ModelData(processTypeConfig), managerConfig)
  }

}


