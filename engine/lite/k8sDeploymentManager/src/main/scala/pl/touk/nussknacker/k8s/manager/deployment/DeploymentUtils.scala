package pl.touk.nussknacker.k8s.manager.deployment

import com.typesafe.config.{Config, ConfigRenderOptions}
import play.api.libs.json.Json
import skuber.apps.v1.Deployment

object DeploymentUtils {

  def createDeployment(deploymentString: String): Deployment = {
    val deploymentSpec = Json.parse(deploymentString).as[Deployment]
    deploymentSpec
  }

  def createDeployment(config: Config): Deployment = {
    val deploymentString = config.root().render(ConfigRenderOptions.concise())
    createDeployment(deploymentString)
  }

}
