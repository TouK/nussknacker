package pl.touk.nussknacker.engine.management

import com.typesafe.config.Config
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import pl.touk.nussknacker.engine.ModelData.ClasspathConfig
import pl.touk.nussknacker.engine.api.{BatchMetaData, TypeSpecificData}
import pl.touk.nussknacker.engine.api.deployment.ProcessManager
import pl.touk.nussknacker.engine.queryablestate.QueryableClient
import pl.touk.nussknacker.engine.{ModelData, ProcessManagerProvider, ProcessingTypeConfig}
import sttp.client.{NothingT, SttpBackend}
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.concurrent.Future

class FlinkBatchProcessManagerProvider extends ProcessManagerProvider {

  import FlinkBatchProcessManagerProvider._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.Ficus._
  import pl.touk.nussknacker.engine.util.config.ConfigEnrichments._

  override def createProcessManager(modelData: ModelData, config: Config): ProcessManager = {
    implicit val backend: SttpBackend[Future, Nothing, NothingT] = AsyncHttpClientFutureBackend.usingConfig(new DefaultAsyncHttpClientConfig.Builder().build())

    val flinkConfig = config.rootAs[FlinkConfig]
    new FlinkBatchRestManager(flinkConfig, modelData)
  }

  override def createQueryableClient(config: Config): Option[QueryableClient] = None

  override def name: String = EngineType

  override def emptyProcessMetadata(isSubprocess: Boolean): TypeSpecificData = {
    val parallelism = if (isSubprocess) None else Some(1)
    BatchMetaData(parallelism)
  }

  override def supportsSignals: Boolean = false
}

object FlinkBatchProcessManagerProvider {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.util.config.FicusReaders._

  val EngineType = "flinkBatch"

  def defaultTypeConfig(config: Config): ProcessingTypeConfig = {
    ProcessingTypeConfig(EngineType,
      config.as[ClasspathConfig]("flinkConfig").urls,
      config.getConfig("flinkConfig"),
      config.getConfig("processConfig"))
  }

  def defaultProcessManager(config: Config): ProcessManager = {
    val typeConfig = defaultTypeConfig(config)
    new FlinkStreamingProcessManagerProvider().createProcessManager(typeConfig.toModelData, typeConfig.engineConfig)
  }
}