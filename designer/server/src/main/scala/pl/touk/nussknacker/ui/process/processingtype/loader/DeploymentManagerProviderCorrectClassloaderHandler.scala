package pl.touk.nussknacker.ui.process.processingtype.loader

import cats.data.ValidatedNel
import com.typesafe.config.Config
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.classloader.DeploymentManagersClassLoader
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.engine.util.ThreadUtils

import scala.concurrent.duration.FiniteDuration

// We wrap DeploymentManagerProvider with this handler to make sure that every method execution will have the correct
// context classloader pinned. It is especially important when some logic around java resources is invoked.
// See for example Source.fromResource, ConfigFactory.parseResources etc.
// Warning! This solution won't work for lazy loaded resources because for them, context classloader will be different.
private[loader] class DeploymentManagerProviderCorrectClassloaderHandler(
    delegate: DeploymentManagerProvider,
    deploymentManagersClassLoader: DeploymentManagersClassLoader
) extends DeploymentManagerProvider {

  override def name: String = ThreadUtils.withContextClassLoader(deploymentManagersClassLoader) { delegate.name }

  override def createDeploymentManager(
      modelDataProvider: BaseModelDataProvider,
      dependencies: DeploymentManagerDependencies,
      deploymentConfig: Config,
      scenarioStateCacheTTL: Option[FiniteDuration]
  ): ValidatedNel[String, DeploymentManager] = {
    ThreadUtils.withContextClassLoader(deploymentManagersClassLoader) {
      delegate.createDeploymentManager(modelDataProvider, dependencies, deploymentConfig, scenarioStateCacheTTL)
    }
  }

  override def metaDataInitializer(config: Config): MetaDataInitializer =
    ThreadUtils.withContextClassLoader(deploymentManagersClassLoader) { delegate.metaDataInitializer(config) }

  override def scenarioPropertiesConfig(config: Config): Map[String, ScenarioPropertyConfig] =
    ThreadUtils.withContextClassLoader(deploymentManagersClassLoader) {
      delegate.scenarioPropertiesConfig(config)
    }

  override def additionalValidators(config: Config): List[CustomProcessValidator] =
    ThreadUtils.withContextClassLoader(deploymentManagersClassLoader) { delegate.additionalValidators(config) }

  override def defaultEngineSetupName: EngineSetupName =
    ThreadUtils.withContextClassLoader(deploymentManagersClassLoader) { delegate.defaultEngineSetupName }

  override def engineSetupIdentity(config: Config): Any =
    ThreadUtils.withContextClassLoader(deploymentManagersClassLoader) { delegate.engineSetupIdentity(config) }

  override def jobsRecoverySettings(config: Config): JobsRecoverySettings = {
    ThreadUtils.withContextClassLoader(deploymentManagersClassLoader) { delegate.jobsRecoverySettings(config) }
  }

}
