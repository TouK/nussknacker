package pl.touk.nussknacker.engine.management

import akka.actor.ActorSystem
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.{BaseModelData, CustomActionsProviderFactoryLoader, DeploymentManagerProvider, EmptyCustomActionsProvider, ModelData, ProcessingTypeConfig, TypeSpecificInitialData}
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, ProcessingTypeDeploymentService}
import pl.touk.nussknacker.engine.api.queryablestate.QueryableClient
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader

class FlinkStreamingDeploymentManagerProvider extends DeploymentManagerProvider {

  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.Ficus._
  import pl.touk.nussknacker.engine.util.config.ConfigEnrichments._

  override def createDeploymentManager(modelData: BaseModelData, config: Config)
                                      (implicit ec: ExecutionContext, actorSystem: ActorSystem,
                                       sttpBackend: SttpBackend[Future, Nothing, NothingT],
                                       deploymentService: ProcessingTypeDeploymentService): DeploymentManager = {
    val flinkConfig = config.rootAs[FlinkConfig]
    val classLoader = new URLClassLoader(modelData.modelClassLoaderUrls, modelData.getClass.getClassLoader)
    val customActionsProviderFactory = CustomActionsProviderFactoryLoader(classLoader)
    val customActionsProvider = customActionsProviderFactory.create(config, deploymentService)
    new FlinkStreamingRestManager(flinkConfig, modelData, customActionsProvider)
  }

  override def createQueryableClient(config: Config): Option[QueryableClient] = {
    val flinkConfig = config.rootAs[FlinkConfig]
    flinkConfig.queryableStateProxyUrl.map(FlinkQueryableClient(_))
  }

  override def name: String = "flinkStreaming"

  override def typeSpecificInitialData: TypeSpecificInitialData = TypeSpecificInitialData(StreamMetaData(Some(1)))

  override def supportsSignals: Boolean = true
}

object FlinkStreamingDeploymentManagerProvider {

  def defaultDeploymentManager(config: Config)
                              (implicit ec: ExecutionContext, actorSystem: ActorSystem,
                               sttpBackend: SttpBackend[Future, Nothing, NothingT], deploymentService: ProcessingTypeDeploymentService): DeploymentManager = {
    val typeConfig = ProcessingTypeConfig.read(config)
    new FlinkStreamingDeploymentManagerProvider().createDeploymentManager(ModelData(typeConfig), typeConfig.deploymentConfig)
  }

}
