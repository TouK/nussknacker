package pl.touk.nussknacker.ui.process.processingtype.loader

import cats.data.ValidatedNel
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.{
  BaseModelData,
  CustomProcessValidator,
  DeploymentManagerDependencies,
  DeploymentManagerProvider,
  MetaDataInitializer
}
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.util.loader.DeploymentManagersClassLoader

import scala.concurrent.duration.FiniteDuration

// We wrap DeploymentManagerProvider with this handler to make sure that every method execution will have the correct
// context classloader pinned. It is especially important when some logic around java resources is invoked.
// See for example Source.fromResource, ConfigFactory.parseResources etc.
// Warning! This solution won't work for lazy loaded resources because for them, context classloader will be different.
private[loader] class DeploymentManagerProviderCorrectClassloaderHandler(
    delegate: DeploymentManagerProvider,
    deploymentManagersClassLoader: DeploymentManagersClassLoader
) extends DeploymentManagerProvider {

  override def name: String = ThreadUtils.withThisAsContextClassLoader(deploymentManagersClassLoader) { delegate.name }

  override def createDeploymentManager(
      modelData: BaseModelData,
      dependencies: DeploymentManagerDependencies,
      deploymentConfig: Config,
      scenarioStateCacheTTL: Option[FiniteDuration]
  ): ValidatedNel[String, DeploymentManager] = {
    ThreadUtils.withThisAsContextClassLoader(deploymentManagersClassLoader) {
      delegate.createDeploymentManager(modelData, dependencies, deploymentConfig, scenarioStateCacheTTL)
    }
  }

  override def metaDataInitializer(config: Config): MetaDataInitializer =
    ThreadUtils.withThisAsContextClassLoader(deploymentManagersClassLoader) { delegate.metaDataInitializer(config) }

  override def scenarioPropertiesConfig(config: Config): Map[String, ScenarioPropertyConfig] =
    ThreadUtils.withThisAsContextClassLoader(deploymentManagersClassLoader) {
      delegate.scenarioPropertiesConfig(config)
    }

  override def additionalValidators(config: Config): List[CustomProcessValidator] =
    ThreadUtils.withThisAsContextClassLoader(deploymentManagersClassLoader) { delegate.additionalValidators(config) }

  override def defaultEngineSetupName: EngineSetupName =
    ThreadUtils.withThisAsContextClassLoader(deploymentManagersClassLoader) { delegate.defaultEngineSetupName }

  override def engineSetupIdentity(config: Config): Any =
    ThreadUtils.withThisAsContextClassLoader(deploymentManagersClassLoader) { delegate.engineSetupIdentity(config) }

}
