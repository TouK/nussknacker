package pl.touk.nussknacker.k8s.manager

import akka.actor.ActorSystem
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData.BaseModelDataExt
import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, ProcessingTypeDeploymentService}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{LiteStreamMetaData, RequestResponseMetaData}
import pl.touk.nussknacker.engine.requestresponse.api.openapi.RequestResponseOpenApiSettings
import pl.touk.nussknacker.engine.{BaseModelData, CustomProcessValidator, DeploymentManagerProvider, TypeSpecificInitialData}
import pl.touk.nussknacker.k8s.manager.RequestResponseSlugUtils.defaultSlug
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

/*
  Each scenario is deployed as Deployment+ConfigMap
  ConfigMap contains: model config with overrides for execution and scenario
 */
class K8sDeploymentManagerProvider extends DeploymentManagerProvider {

  override def createDeploymentManager(modelData: BaseModelData, config: Config)
                                      (implicit ec: ExecutionContext, actorSystem: ActorSystem,
                                       sttpBackend: SttpBackend[Future, Nothing, NothingT],
                                       deploymentService: ProcessingTypeDeploymentService): DeploymentManager = {
    new K8sDeploymentManager(modelData.asInvokableModelData, K8sDeploymentManagerConfig.parse(config))
  }

  private val steamingInitialMetData = TypeSpecificInitialData(LiteStreamMetaData(Some(1)))

  override def typeSpecificInitialData(config: Config): TypeSpecificInitialData = {
    forMode(config)(
      _ => steamingInitialMetData,
      config => (scenarioName: ProcessName, _: String) => RequestResponseMetaData(Some(defaultSlug(scenarioName,
        config.nussknackerInstanceName)))
    )
  }

  override def additionalPropertiesConfig(config: Config): Map[String, AdditionalPropertyConfig] = forMode(config)(
    _ => Map.empty,
    _ => RequestResponseOpenApiSettings.additionalPropertiesConfig
  )

  override def additionalValidators(config: Config): List[CustomProcessValidator] = forMode(config)(
    _ => Nil,
    config => List(new RequestResponseScenarioValidator(config.nussknackerInstanceName))
  )

  override def name: String = "lite-k8s"

  private def forMode[T](config: Config)(streaming: K8sDeploymentManagerConfig => T, requestResponse: K8sDeploymentManagerConfig => T): T = {
    // TODO: mode field won't be needed if we add scenarioType to TypeSpecificInitialData.forScenario
    //       and add scenarioType -> mode mapping with reasonable defaults to configuration
    val k8sConfig = K8sDeploymentManagerConfig.parse(config)
    config.getString("mode") match {
      case "streaming" => streaming(k8sConfig)
      case "request-response" => requestResponse(k8sConfig)
      case other => throw new IllegalArgumentException(s"Unsupported mode: $other")
    }
  }
}