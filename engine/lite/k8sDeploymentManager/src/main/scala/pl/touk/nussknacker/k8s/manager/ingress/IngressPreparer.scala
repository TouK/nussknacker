package pl.touk.nussknacker.k8s.manager.ingress

import pl.touk.nussknacker.engine.api.{LiteStreamMetaData, ProcessVersion, RequestResponseMetaData, TypeSpecificData}
import pl.touk.nussknacker.k8s.manager.K8sDeploymentManager.{labelsForScenario, objectNamePrefixedWithNussknackerInstanceNameWithoutSanitization}
import pl.touk.nussknacker.k8s.manager.K8sUtils.sanitizeObjectName
import pl.touk.nussknacker.k8s.manager.RequestResponseSlugUtils
import pl.touk.nussknacker.k8s.manager.ingress.IngressPreparer.rewriteAnnotation
import skuber.ObjectMeta
import skuber.networking.v1.Ingress

case class IngressTlsConfig(enabled: Boolean, secretName: Option[String]) {
  if (enabled) require(secretName.isDefined, "When TLS is enabled, secretName is required.")
}

case class IngressConfig(host: String, tls: Option[IngressTlsConfig] = None, annotations: Map[String, String] = Map.empty)

class IngressPreparer(config: IngressConfig, nuInstanceName: Option[String]) {

  def prepare(processVersion: ProcessVersion, typeSpecificData: TypeSpecificData, serviceName: String, servicePort: Int): Option[Ingress] =
    typeSpecificData match {
      case _: LiteStreamMetaData => None
      case rrMetaData: RequestResponseMetaData => Some(prepareRequestResponseIngress(processVersion, rrMetaData, serviceName, servicePort))
      case other => throw new IllegalArgumentException("Not supported scenario meta data type: " + other)
    }

  private def prepareRequestResponseIngress(processVersion: ProcessVersion, rrMetaData: RequestResponseMetaData, serviceName: String, servicePort: Int): Ingress = {
    val objectName = IngressPreparer.name(nuInstanceName, s"scenario-${processVersion.processName.value}")
    val labels = labelsForScenario(processVersion, nuInstanceName)
    val slug = RequestResponseSlugUtils.determineSlug(processVersion.processName, rrMetaData, nuInstanceName)

    Ingress(
      metadata = ObjectMeta(name = objectName, labels = labels, annotations = rewriteAnnotation ++ config.annotations),
      spec = Some(Ingress.Spec(
        rules = List(Ingress.Rule(Some(config.host), Ingress.HttpRule(paths = List(Ingress.Path(
          path = s"/$slug" + "(/|$)(.*)",
          backend = Ingress.Backend(Some(Ingress.ServiceType(serviceName, Ingress.Port(number = Some(servicePort))))),
          pathType = Ingress.PathType.Prefix))))),
        tls = config.tls.filter(_.enabled).map(conf => Ingress.TLS(hosts = List(config.host), secretName = conf.secretName)).toList
      ))
    )
  }

}

object IngressPreparer {

  private[manager] def name(nussknackerInstanceName: Option[String], scenarioName: String): String =
    sanitizeObjectName(nameWithoutSanitization(nussknackerInstanceName, scenarioName))

  private[manager] def nameWithoutSanitization(nussknackerInstanceName: Option[String], scenarioName: String): String =
    objectNamePrefixedWithNussknackerInstanceNameWithoutSanitization(nussknackerInstanceName, scenarioName)

  private[manager] val rewriteAnnotation = Map("nginx.ingress.kubernetes.io/rewrite-target" -> "/$2")
}