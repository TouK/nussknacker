package pl.touk.nussknacker.engine.management

import akka.actor.ActorSystem
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.{DeploymentManagerProvider, ModelData, ProcessingTypeConfig, TypeSpecificInitialData}
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, ScenarioSpecificData, StreamMetaData}
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.api.queryablestate.QueryableClient
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

class FlinkStreamingDeploymentManagerProvider extends DeploymentManagerProvider {

  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.Ficus._
  import pl.touk.nussknacker.engine.util.config.ConfigEnrichments._

  override def createDeploymentManager(modelData: ModelData, config: Config)
                                      (implicit ec: ExecutionContext, actorSystem: ActorSystem, sttpBackend: SttpBackend[Future, Nothing, NothingT]): DeploymentManager = {
    val flinkConfig = config.rootAs[FlinkConfig]
    new FlinkStreamingRestManager(flinkConfig, modelData)
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
                              (implicit ec: ExecutionContext, actorSystem: ActorSystem, sttpBackend: SttpBackend[Future, Nothing, NothingT]): DeploymentManager = {
    val typeConfig = ProcessingTypeConfig.read(config)
    new FlinkStreamingDeploymentManagerProvider().createDeploymentManager(typeConfig.toModelData, typeConfig.deploymentConfig)
  }
}