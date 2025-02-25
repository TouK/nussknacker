package pl.touk.nussknacker.ui.process.processingtype.loader

import cats.data.ValidatedNel
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.{
  BaseModelData,
  CustomProcessValidator,
  DeploymentManagerDependencies,
  DeploymentManagerProvider,
  MetaDataInitializer
}
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.util.loader.DeploymentManagersClassLoader

import scala.concurrent.duration.FiniteDuration

// We wrap created DeploymentManagerProvider with this handler to make sure that DM creation logic will use
// correct context classloader
class DeploymentManagerProviderCorrectClassloaderHandler(
    delegate: DeploymentManagerProvider,
    deploymentManagersClassLoader: DeploymentManagersClassLoader
) extends DeploymentManagerProvider {

  override def name: String = delegate.name

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

  override def metaDataInitializer(config: Config): MetaDataInitializer = delegate.metaDataInitializer(config)

  override def scenarioPropertiesConfig(config: Config): Map[String, ScenarioPropertyConfig] =
    delegate.scenarioPropertiesConfig(config)

  override def additionalValidators(config: Config): List[CustomProcessValidator] =
    delegate.additionalValidators(config)

  override def defaultEngineSetupName: EngineSetupName = delegate.defaultEngineSetupName

  override def engineSetupIdentity(config: Config): Any = delegate.engineSetupIdentity(config)
}
