package pl.touk.nussknacker.k8s.manager

import akka.actor.ActorSystem
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData.BaseModelDataExt
import pl.touk.nussknacker.engine.api.deployment.cache.CachingProcessStateDeploymentManager
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, ProcessingTypeDeploymentService}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.engine.{BaseModelData, CustomProcessValidator}
import pl.touk.nussknacker.k8s.manager.RequestResponseSlugUtils.defaultSlug
import pl.touk.nussknacker.lite.manager.LiteDeploymentManagerProvider
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

/*
  Each scenario is deployed as Deployment+ConfigMap
  ConfigMap contains: model config with overrides for execution and scenario

  Currently this DM uses the default engineSetupIdentity. It is done this way because we use
  the default K8s context.
 */
class K8sDeploymentManagerProvider extends LiteDeploymentManagerProvider {

  override def createDeploymentManager(modelData: BaseModelData, config: Config)(
      implicit ec: ExecutionContext,
      actorSystem: ActorSystem,
      sttpBackend: SttpBackend[Future, Any],
      deploymentService: ProcessingTypeDeploymentService
  ): DeploymentManager = {
    CachingProcessStateDeploymentManager.wrapWithCachingIfNeeded(
      new K8sDeploymentManager(modelData.asInvokableModelData, K8sDeploymentManagerConfig.parse(config), config),
      config
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
