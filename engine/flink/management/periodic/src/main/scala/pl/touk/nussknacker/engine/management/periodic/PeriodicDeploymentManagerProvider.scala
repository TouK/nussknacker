package pl.touk.nussknacker.engine.management.periodic

import akka.actor.ActorSystem
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, DeploymentService}
import pl.touk.nussknacker.engine.api.queryablestate.QueryableClient
import pl.touk.nussknacker.engine.management.FlinkConfig
import pl.touk.nussknacker.engine.management.periodic.service._
import pl.touk.nussknacker.engine.util.config.ConfigEnrichments.RichConfig
import pl.touk.nussknacker.engine.{DeploymentManagerProvider, ModelData, TypeSpecificInitialData}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

class PeriodicDeploymentManagerProvider(delegate: DeploymentManagerProvider,
                                        schedulePropertyExtractorFactory: SchedulePropertyExtractorFactory = _ => CronSchedulePropertyExtractor(),
                                        processConfigEnricherFactory: ProcessConfigEnricherFactory = ProcessConfigEnricherFactory.noOp,
                                        listenerFactory: PeriodicProcessListenerFactory = EmptyPeriodicProcessListenerFactory,
                                        additionalDeploymentDataProvider: AdditionalDeploymentDataProvider = DefaultAdditionalDeploymentDataProvider,
                                        customActionsProviderFactory: PeriodicCustomActionsProviderFactory = PeriodicCustomActionsProviderFactory.noOp
                                       ) extends DeploymentManagerProvider with LazyLogging {

  override def name: String = s"${delegate.name}Periodic"

  override def createDeploymentManager(modelData: ModelData, config: Config)
                                      (implicit ec: ExecutionContext, actorSystem: ActorSystem,
                                       sttpBackend: SttpBackend[Future, Nothing, NothingT], deploymentService: DeploymentService): DeploymentManager = {
    logger.info("Creating periodic scenario manager")
    val delegateDeploymentManager = delegate.createDeploymentManager(modelData, config)

    import net.ceedubs.ficus.Ficus._
    import net.ceedubs.ficus.readers.ArbitraryTypeReader._
    val periodicBatchConfig = config.as[PeriodicBatchConfig]("deploymentManager")
    val flinkConfig = config.rootAs[FlinkConfig]
    PeriodicDeploymentManager(
      delegate = delegateDeploymentManager,
      schedulePropertyExtractorFactory = schedulePropertyExtractorFactory,
      processConfigEnricherFactory = processConfigEnricherFactory,
      periodicBatchConfig = periodicBatchConfig,
      flinkConfig = flinkConfig,
      originalConfig = config,
      modelData = modelData,
      listenerFactory,
      additionalDeploymentDataProvider,
      customActionsProviderFactory
    )
  }

  override def createQueryableClient(config: Config): Option[QueryableClient] = delegate.createQueryableClient(config)

  override def typeSpecificInitialData: TypeSpecificInitialData = delegate.typeSpecificInitialData

  override def supportsSignals: Boolean = delegate.supportsSignals
}
