package pl.touk.nussknacker.k8s.manager.deployment

import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import play.api.libs.json.Json
import skuber.apps.v1.Deployment

import java.net.URL

object DeploymentUtils {

  def parseDeploymentWithFallback(config: Config, defaultMinimalDeploymentUrl: URL): Deployment = {
    val defaultMinimalDeploymentConfig = ConfigFactory.parseURL(defaultMinimalDeploymentUrl)
    val mergedConfig = config.withFallback(defaultMinimalDeploymentConfig)
    val deploymentString = mergedConfig.root().render(ConfigRenderOptions.concise())
    parseDeployment(deploymentString)
  }

  def parseDeployment(deploymentString: String): Deployment = Json.parse(deploymentString).as[Deployment]

}
