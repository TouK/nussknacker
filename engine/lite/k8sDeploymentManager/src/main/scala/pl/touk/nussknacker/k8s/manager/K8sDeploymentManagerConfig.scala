package pl.touk.nussknacker.k8s.manager

import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.nussknacker.engine.util.config.ConfigEnrichments.RichConfig
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.k8s.manager.deployment.K8sScalingConfig
import pl.touk.nussknacker.k8s.manager.ingress.IngressConfig

import scala.concurrent.duration._

import K8sScalingConfig.valueReader

object K8sDeploymentManagerConfig {
  def parse(config: Config): K8sDeploymentManagerConfig = config.rootAs[K8sDeploymentManagerConfig]
}

case class K8sDeploymentManagerConfig(
    dockerImageName: String = "touk/nussknacker-lite-runtime-app",
    dockerImageTag: String = s"${BuildInfo.version}_scala-${ScalaMajorVersionConfig.scalaMajorVersion}",
    scalingConfig: Option[K8sScalingConfig] = None,
    configExecutionOverrides: Config = ConfigFactory.empty(),
    k8sDeploymentConfig: Config = ConfigFactory.empty(),
    nussknackerInstanceName: Option[String] = None,
    logbackConfigPath: Option[String] = None,
    commonConfigMapForLogback: Option[String] = None,
    ingress: Option[IngressConfig] = None,
    servicePort: Int = 80,
    scenarioStateIdleTimeout: FiniteDuration = 3 seconds
)
