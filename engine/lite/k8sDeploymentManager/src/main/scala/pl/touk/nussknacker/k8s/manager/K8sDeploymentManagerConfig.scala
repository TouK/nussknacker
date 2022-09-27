package pl.touk.nussknacker.k8s.manager

import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.util.config.ConfigEnrichments.RichConfig
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.k8s.manager.deployment.K8sScalingConfig
import pl.touk.nussknacker.k8s.manager.ingress.IngressConfig
import K8sScalingConfig.valueReader
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

object K8sDeploymentManagerConfig {
  def parse(config: Config): K8sDeploymentManagerConfig = config.rootAs[K8sDeploymentManagerConfig]
}

case class K8sDeploymentManagerConfig(dockerImageName: String = "touk/nussknacker-lite-runtime-app",
                                      dockerImageTag: String = BuildInfo.version,
                                      scalingConfig: Option[K8sScalingConfig] = None,
                                      configExecutionOverrides: Config = ConfigFactory.empty(),
                                      k8sDeploymentConfig: Config = ConfigFactory.empty(),
                                      nussknackerInstanceName: Option[String] = None,
                                      logbackConfigPath: Option[String] = None,
                                      commonConfigMapForLogback: Option[String] = None,
                                      ingress: Option[IngressConfig] = None,
                                      servicePort: Int = 80)
