package pl.touk.nussknacker.engine.management

import com.typesafe.config.{Config, ConfigFactory}
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import pl.touk.nussknacker.engine.ModelData.ClasspathConfig
import pl.touk.nussknacker.engine.{ModelData, ProcessManagerProvider, ProcessingTypeConfig}
import pl.touk.nussknacker.engine.api.{StreamMetaData, TypeSpecificData}
import pl.touk.nussknacker.engine.api.deployment.ProcessManager
import pl.touk.nussknacker.engine.flink.queryablestate.FlinkQueryableClient
import pl.touk.nussknacker.engine.queryablestate.QueryableClient
import sttp.client.{NothingT, SttpBackend}
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.concurrent.Future

class FlinkProcessManagerProvider extends ProcessManagerProvider {

  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.Ficus._
  import pl.touk.nussknacker.engine.util.config.ConfigEnrichments._

  override def createProcessManager(modelData: ModelData, config: Config): ProcessManager = {
    implicit val backend: SttpBackend[Future, Nothing, NothingT] = AsyncHttpClientFutureBackend.usingConfig(new DefaultAsyncHttpClientConfig.Builder().build())

    val flinkConfig = config.rootAs[FlinkConfig]
    new FlinkRestManager(flinkConfig, modelData)
  }

  override def createQueryableClient(config: Config): Option[QueryableClient] = {
    val flinkConfig = config.rootAs[FlinkConfig]
    flinkConfig.queryableStateProxyUrl.map(FlinkQueryableClient(_))
  }

  override def name: String = "flinkStreaming"

  override def emptyProcessMetadata(isSubprocess: Boolean): TypeSpecificData
  = StreamMetaData(parallelism = if (isSubprocess) None else Some(1))

  override def supportsSignals: Boolean = true
}

object FlinkProcessManagerProvider {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.util.config.FicusReaders._

  def defaultTypeConfig(config: Config): ProcessingTypeConfig = {
    ProcessingTypeConfig("flinkStreaming",
      config.as[ClasspathConfig]("flinkConfig").urls,
      config.getConfig("flinkConfig"),
      config.getConfig("processConfig"))
  }

  def defaultModelData(config: Config): ModelData = defaultTypeConfig(config).toModelData

  def defaultProcessManager(config: Config): ProcessManager = {
    val typeConfig = defaultTypeConfig(config)
    new FlinkProcessManagerProvider().createProcessManager(typeConfig.toModelData, typeConfig.engineConfig)
  }
}