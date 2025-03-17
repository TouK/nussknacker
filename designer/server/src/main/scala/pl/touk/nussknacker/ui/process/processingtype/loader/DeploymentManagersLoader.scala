package pl.touk.nussknacker.ui.process.processingtype.loader

import pl.touk.nussknacker.engine.{DeploymentManagerDependencies, DeploymentManagerProvider, ProcessingTypeConfig}
import pl.touk.nussknacker.engine.api.deployment.{NoSchedulingSupport, SchedulingSupported}
import pl.touk.nussknacker.engine.api.deployment.cache.ScenarioStateCachingConfig
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.loader.{DeploymentManagersClassLoader, ScalaServiceLoader}
import pl.touk.nussknacker.ui.configloader.ProcessingTypeConfigs
import pl.touk.nussknacker.ui.factory.InfrastructureServices
import pl.touk.nussknacker.ui.process.periodic.PeriodicDeploymentManagerDecorator
import pl.touk.nussknacker.ui.process.processingtype.EngineNameInputData
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeData.SchedulingForProcessingType

object DeploymentManagersLoader {
  /*
  def load(
      processingTypeConfigs: ProcessingTypeConfigs,
      infrastructureServices: InfrastructureServices,
      deploymentManagersClassLoader: DeploymentManagersClassLoader,
      deploymentManagerDependencies: DeploymentManagerDependencies
  ) = {
    // This step with splitting DeploymentManagerProvider loading for all processing types
    // and after that creating ProcessingTypeData is done because of the deduplication of deployments
    // See DeploymentManagerProvider.engineSetupIdentity
    val providerWithNameInputData =
      processingTypeConfigs.configByProcessingType.mapValuesNow { processingTypeConfig =>
        val deploymentManagerProvider =
          createDeploymentManagerProvider(deploymentManagersClassLoader, processingTypeConfig)
        val nameInputData = EngineNameInputData(
          deploymentManagerProvider.defaultEngineSetupName,
          deploymentManagerProvider.engineSetupIdentity(processingTypeConfig.deploymentConfig),
          processingTypeConfig.engineSetupName
        )

        val schedulingForProcessingType =
          if (processingTypeConfig.deploymentConfig.hasPath("scheduling") &&
            processingTypeConfig.deploymentConfig.getBoolean("scheduling.enabled")) {
            SchedulingForProcessingType.Available(infrastructureServices.dbRef)
          } else {
            SchedulingForProcessingType.NotAvailable
          }

        val scenarioStateCacheTTL = ScenarioStateCachingConfig.extractScenarioStateCacheTTL(processingTypeConfig.deploymentConfig)

        val validDeploymentManager = for {
          deploymentManager <-
            deploymentManagerProvider.createDeploymentManager(
              modelData,
              deploymentManagerDependencies,
              processingTypeConfig.deploymentConfig,
              scenarioStateCacheTTL
            )
          decoratedDeploymentManager = schedulingForProcessingType match {
            case SchedulingForProcessingType.Available(dbRef) =>
              deploymentManager.schedulingSupport match {
                case supported: SchedulingSupported =>
                  PeriodicDeploymentManagerDecorator.decorate(
                    underlying = deploymentManager,
                    schedulingSupported = supported,
                    deploymentConfig = processingTypeConfig.deploymentConfig,
                    dependencies = deploymentManagerDependencies,
                    dbRef = dbRef,
                  )
                case NoSchedulingSupport =>
                  throw new IllegalStateException(
                    s"DeploymentManager ${deploymentManagerProvider.name} does not support periodic execution"
                  )
              }

            case SchedulingForProcessingType.NotAvailable =>
              deploymentManager
          }
        } yield decoratedDeploymentManager

        (deploymentManagerProvider, nameInputData)
      }
  }

  private def createDeploymentManagerProvider(
      deploymentManagersClassLoader: DeploymentManagersClassLoader,
      typeConfig: ProcessingTypeConfig
  ): DeploymentManagerProvider = {
    val loadedProvider = ScalaServiceLoader.loadNamed[DeploymentManagerProvider](
      typeConfig.deploymentManagerType,
      deploymentManagersClassLoader
    )
    new DeploymentManagerProviderCorrectClassloaderHandler(loadedProvider, deploymentManagersClassLoader)
  }
   */
}
