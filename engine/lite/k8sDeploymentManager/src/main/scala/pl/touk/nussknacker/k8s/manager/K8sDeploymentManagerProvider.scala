package pl.touk.nussknacker.k8s.manager

import cats.data.{Validated, ValidatedNel}
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.{BaseModelData, CustomProcessValidator, DeploymentManagerDependencies}
import pl.touk.nussknacker.engine.ModelData.BaseModelDataExt
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.api.deployment.cache.CachingProcessStateDeploymentManager
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.k8s.manager.RequestResponseSlugUtils.defaultSlug
import pl.touk.nussknacker.lite.manager.LiteDeploymentManagerProvider

import scala.concurrent.duration.FiniteDuration

/*
  Each scenario is deployed as Deployment+ConfigMap
  ConfigMap contains: model config with overrides for execution and scenario

  Currently this DM uses the default engineSetupIdentity. It is done this way because we use
  the default K8s context.
 */
class K8sDeploymentManagerProvider extends LiteDeploymentManagerProvider {

  override def createDeploymentManager(
      modelData: BaseModelData,
      dependencies: DeploymentManagerDependencies,
      config: Config,
      scenarioStateCacheTTL: Option[FiniteDuration]
  ): ValidatedNel[String, DeploymentManager] = {
    Validated.valid(
      CachingProcessStateDeploymentManager.wrapWithCachingIfNeeded(
        new K8sDeploymentManager(
          modelData,
          K8sDeploymentManagerConfig.parse(config),
          config,
          dependencies
        ),
        scenarioStateCacheTTL
      )
    )
  }

  override protected def defaultRequestResponseSlug(scenarioName: ProcessName, config: Config): String = {
    val k8sConfig = K8sDeploymentManagerConfig.parse(config)
    defaultSlug(scenarioName, k8sConfig.nussknackerInstanceName)
  }

  override def additionalValidators(config: Config): List[CustomProcessValidator] = forMode(config)(
    Nil,
    List(RequestResponseScenarioValidator(K8sDeploymentManagerConfig.parse(config)))
  )

  override def name: String = "lite-k8s"

}
