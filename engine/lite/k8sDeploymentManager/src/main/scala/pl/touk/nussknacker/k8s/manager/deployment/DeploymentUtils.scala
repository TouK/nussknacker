package pl.touk.nussknacker.k8s.manager.deployment

import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions, ConfigValue, ConfigValueFactory}
import play.api.libs.json.Json
import skuber.apps.v1.Deployment

import java.net.URL
import scala.jdk.CollectionConverters._

object DeploymentUtils {
  val containersPath = "spec.template.spec.containers"
  private val imageValuePlaceholder = ConfigValueFactory.fromAnyRef("placeholder")

  def parseDeploymentWithFallback(config: Config, defaultMinimalDeploymentUrl: URL): Deployment = {
    val defaultMinimalDeploymentConfig = ConfigFactory.parseURL(defaultMinimalDeploymentUrl)
    val mergedConfig = config.withFallback(defaultMinimalDeploymentConfig)

    val containersWithMissingImage = mergedConfig.getConfigList(containersPath).asScala.map(_.withFallback(ConfigFactory.empty().withValue("image", imageValuePlaceholder)))
    val containersValues = containersWithMissingImage.map(_.root()).map(ConfigValueFactory.fromAnyRef(_)).asJava
    val finalConfig = mergedConfig.withValue(containersPath, ConfigValueFactory.fromIterable(containersValues))
    val deploymentString = finalConfig.root().render(ConfigRenderOptions.concise())
    parseDeployment(deploymentString)
  }

  def parseDeployment(deploymentString: String): Deployment = Json.parse(deploymentString).as[Deployment]

}
