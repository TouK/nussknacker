package pl.touk.nussknacker.engine

import akka.actor.ActorSystem
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, ProcessingTypeDeploymentService}
import pl.touk.nussknacker.engine.api.queryablestate.QueryableClient
import _root_.sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

case class ProcessingTypeData(deploymentManager: DeploymentManager,
                              modelData: ModelData,
                              typeSpecificInitialData: TypeSpecificInitialData,
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
      deploymentManagerProvider.typeSpecificInitialData,
      queryableClient,
      deploymentManagerProvider.supportsSignals)
  }

  def createProcessingTypeData(deploymentManagerProvider: DeploymentManagerProvider, processTypeConfig: ProcessingTypeConfig)
                              (implicit ec: ExecutionContext, actorSystem: ActorSystem,
                               sttpBackend: SttpBackend[Future, Nothing, NothingT],
                               deploymentService: ProcessingTypeDeploymentService): ProcessingTypeData = {
    val managerConfig = processTypeConfig.deploymentConfig
    createProcessingTypeData(deploymentManagerProvider, ModelData(processTypeConfig), managerConfig)
  }

}


