package pl.touk.nussknacker.engine.management

import com.typesafe.config.Config
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import pl.touk.nussknacker.engine.{DeploymentManagerProvider, TypeSpecificDataInitializer, ModelData, ProcessingTypeConfig}
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, ScenarioSpecificData, StreamMetaData}
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.api.queryablestate.QueryableClient
import sttp.client.{NothingT, SttpBackend}
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.concurrent.Future

class FlinkStreamingDeploymentManagerProvider extends DeploymentManagerProvider {

  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.Ficus._
  import pl.touk.nussknacker.engine.util.config.ConfigEnrichments._

  override def createDeploymentManager(modelData: ModelData, config: Config): DeploymentManager = {
    implicit val backend: SttpBackend[Future, Nothing, NothingT] = AsyncHttpClientFutureBackend.usingConfig(new DefaultAsyncHttpClientConfig.Builder().build())

    val flinkConfig = config.rootAs[FlinkConfig]
    new FlinkStreamingRestManager(flinkConfig, modelData)
  }

  override def createQueryableClient(config: Config): Option[QueryableClient] = {
    val flinkConfig = config.rootAs[FlinkConfig]
    flinkConfig.queryableStateProxyUrl.map(FlinkQueryableClient(_))
  }

  override def name: String = "flinkStreaming"

  override def typeSpecificDataInitializer: TypeSpecificDataInitializer = new TypeSpecificDataInitializer {
    override def forScenario: ScenarioSpecificData = StreamMetaData(Some(1))
    override def forFragment: FragmentSpecificData = FragmentSpecificData(None)
  }

  override def supportsSignals: Boolean = true
}

object FlinkStreamingDeploymentManagerProvider {

  def defaultDeploymentManager(config: Config): DeploymentManager = {
    val typeConfig = ProcessingTypeConfig.read(config)
    new FlinkStreamingDeploymentManagerProvider().createDeploymentManager(typeConfig.toModelData, typeConfig.deploymentConfig)
  }
}