package pl.touk.nussknacker.k8s.manager.deployment

import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import play.api.libs.json.Json
import skuber.apps.v1.Deployment

object DeploymentUtils {

  def parseDeploymentWithFallback(config: Config): Deployment = {
    val defaultMinimalDeploymentConfig = ConfigFactory.parseURL(getClass.getResource(s"/defaultMinimalDeployment.conf"))
    val mergedConfig = config.withFallback(defaultMinimalDeploymentConfig)
    val deploymentString = mergedConfig.root().render(ConfigRenderOptions.concise())
    parseDeployment(deploymentString)
  }

  def parseDeployment(deploymentString: String): Deployment = Json.parse(deploymentString).as[Deployment]

}
