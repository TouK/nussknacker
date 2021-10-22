package pl.touk.nussknacker.engine.management.periodic

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.TypeSpecificData
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.api.queryablestate.QueryableClient
import pl.touk.nussknacker.engine.management.FlinkConfig
import pl.touk.nussknacker.engine.management.periodic.service.{AdditionalDeploymentDataProvider, DefaultAdditionalDeploymentDataProvider, EmptyPeriodicProcessListenerFactory, PeriodicProcessListenerFactory, ProcessConfigEnricherFactory}
import pl.touk.nussknacker.engine.util.config.ConfigEnrichments.RichConfig
import pl.touk.nussknacker.engine.{DeploymentManagerProvider, TypeSpecificDataInitializer, ModelData}

class PeriodicDeploymentManagerProvider(delegate: DeploymentManagerProvider,
                                        schedulePropertyExtractorFactory: SchedulePropertyExtractorFactory = _ => CronSchedulePropertyExtractor(),
                                        processConfigEnricherFactory: ProcessConfigEnricherFactory = ProcessConfigEnricherFactory.noOp,
                                        listenerFactory: PeriodicProcessListenerFactory = EmptyPeriodicProcessListenerFactory,
                                        additionalDeploymentDataProvider: AdditionalDeploymentDataProvider = DefaultAdditionalDeploymentDataProvider
                                       ) extends DeploymentManagerProvider with LazyLogging {

  override def name: String = s"${delegate.name}Periodic"

  override def createDeploymentManager(modelData: ModelData, config: Config): DeploymentManager = {
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
      additionalDeploymentDataProvider
    )
  }

  override def createQueryableClient(config: Config): Option[QueryableClient] = delegate.createQueryableClient(config)

  override def typeSpecificDataInitializer: TypeSpecificDataInitializer = delegate.typeSpecificDataInitializer

  override def supportsSignals: Boolean = delegate.supportsSignals
}
