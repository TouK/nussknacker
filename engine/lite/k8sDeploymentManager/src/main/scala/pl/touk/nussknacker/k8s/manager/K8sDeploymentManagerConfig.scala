package pl.touk.nussknacker.k8s.manager

import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.engine.util.config.ConfigEnrichments.RichConfig
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.k8s.manager.deployment.K8sScalingConfig
import pl.touk.nussknacker.k8s.manager.ingress.IngressConfig

import scala.concurrent.duration._

object K8sDeploymentManagerConfig {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  private implicit val scalingConfigValueReader: ValueReader[K8sScalingConfig] = K8sScalingConfig.valueReader

  private implicit val optionalNussknackerInstanceNameValueReader: ValueReader[OptionalNussknackerInstanceName] =
    OptionalNussknackerInstanceName.valueReader

  def parse(config: Config): K8sDeploymentManagerConfig = config.rootAs[K8sDeploymentManagerConfig]

}

case class K8sDeploymentManagerConfig(
    dockerImageName: String = "touk/nussknacker-lite-runtime-app",
    dockerImageTag: String = s"${BuildInfo.version}_scala-${ScalaMajorVersionConfig.scalaMajorVersion}",
    scalingConfig: Option[K8sScalingConfig] = None,
    configExecutionOverrides: Config = ConfigFactory.empty(),
    k8sDeploymentConfig: Config = ConfigFactory.empty(),
    nussknackerInstanceName: OptionalNussknackerInstanceName = OptionalNussknackerInstanceName.empty,
    logbackConfigPath: Option[String] = None,
    commonConfigMapForLogback: Option[String] = None,
    ingress: Option[IngressConfig] = None,
    servicePort: Int = 80,
    scenarioStateIdleTimeout: FiniteDuration = 3 seconds
)
