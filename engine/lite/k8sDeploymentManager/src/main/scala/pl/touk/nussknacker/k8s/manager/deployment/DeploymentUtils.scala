package pl.touk.nussknacker.k8s.manager.deployment

import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import org.apache.commons.io.IOUtils
import play.api.libs.json.Json
import skuber.apps.v1.Deployment

object DeploymentUtils {

  def createDeployment(deploymentString: String): Deployment = {
    val deploymentSpec = Json.parse(deploymentString).as[Deployment]
    deploymentSpec
  }

  def createDeployment(config: Config): Deployment = {
    val minimalPlaceholderConfig = ConfigFactory.parseString(Json.parse(IOUtils.toString(getClass.getResourceAsStream(s"/deploymentMinimal.json"))).toString())
    val mergedConfig = config.withFallback(minimalPlaceholderConfig)
    val deploymentString = mergedConfig.root().render(ConfigRenderOptions.concise())
    createDeployment(deploymentString)
  }
}
