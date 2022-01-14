package pl.touk.nussknacker.k8s.manager.deployment

import com.typesafe.config.{Config, ConfigRenderOptions}
import play.api.libs.json.{JsObject, Json}
import skuber.apps.v1.Deployment
import skuber.json.apps.format.depSpecFmt

object DeploymentSpecUtils {

  def createDeploymentSpecV1(deploymentSpecString: String): Deployment.Spec = {
    val deploymentSpec = Json.parse(deploymentSpecString).as[Deployment.Spec]
    deploymentSpec
  }

  def createDeploymentSpecV1(config: Config): Deployment.Spec = {
    val deploymentSpec = Json.parse(config.root().render(ConfigRenderOptions.concise())).as[Deployment.Spec]
    deploymentSpec
  }

  def createDeploymentSpec(config: Config): skuber.apps.Deployment.Spec = {
    val deploymentSpec = Json.parse(config.root().render(ConfigRenderOptions.concise())).as[skuber.apps.Deployment.Spec]
    deploymentSpec
  }

  def mergeDeploymentSpec(deploymentSpec: Option[Deployment.Spec], k8sDeploymentSpec: Deployment.Spec): Deployment.Spec = {
    val deploymentSpecJson = Json.toJson(deploymentSpec)
    val k8sDeploymentSpecJson = Json.toJson(k8sDeploymentSpec)
    val mergedDeploymentSpecJson = deploymentSpecJson.asInstanceOf[JsObject] deepMerge k8sDeploymentSpecJson.asInstanceOf[JsObject]
    val mergedDeploymentSpec = mergedDeploymentSpecJson.as[Deployment.Spec]
    mergedDeploymentSpec
  }
}
