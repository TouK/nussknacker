package pl.touk.nussknacker.k8s.manager.service

import pl.touk.nussknacker.engine.api.{LiteStreamMetaData, MetaData, ProcessVersion, RequestResponseMetaData}
import pl.touk.nussknacker.k8s.manager.K8sDeploymentManager._
import pl.touk.nussknacker.k8s.manager.K8sDeploymentManagerConfig
import pl.touk.nussknacker.k8s.manager.K8sUtils.sanitizeObjectName
import pl.touk.nussknacker.k8s.manager.RequestResponseSlugUtils.determineSlug
import pl.touk.nussknacker.k8s.manager.service.ServicePreparer.{runtimePodTargetPort, serviceName}
import skuber.{ObjectMeta, Service}
import skuber.Service.Port

class ServicePreparer(config: K8sDeploymentManagerConfig) {

  def prepare(processVersion: ProcessVersion, metaData: MetaData): Option[Service] = {
    metaData.typeSpecificData match {
      case _: LiteStreamMetaData =>
        None
      case rrMetaData: RequestResponseMetaData =>
        Some(prepareRequestResponseService(processVersion, rrMetaData))
      case other =>
        throw new IllegalArgumentException("Not supported scenario meta data type: " + other)
    }
  }

  private def prepareRequestResponseService(
      processVersion: ProcessVersion,
      rrMetaData: RequestResponseMetaData
  ): Service = {
    val objectName = serviceName(
      config.nussknackerInstanceName,
      determineSlug(processVersion.processName, rrMetaData, config.nussknackerInstanceName)
    )
    val annotations = versionAnnotationForScenario(processVersion)
    val labels      = labelsForScenario(processVersion, config.nussknackerInstanceName)
    val selectors = Map(
      // here we use id to avoid sanitization problems
      scenarioIdLabel -> processVersion.processId.value.toString
    ) ++ config.nussknackerInstanceName.map(nussknackerInstanceNameLabel -> _)

    Service(
      metadata = ObjectMeta(name = objectName, labels = labels, annotations = annotations),
      spec = Some(
        Service.Spec(
          selector = selectors,
          ports = List(Port(port = config.servicePort, targetPort = Some(Left(runtimePodTargetPort))))
        )
      )
    )
  }

}

object ServicePreparer {
  // see http.port in runtimes image's application.conf
  val runtimePodTargetPort = 8080

  private[manager] def serviceName(nussknackerInstanceName: Option[String], slug: String): String =
    sanitizeObjectName(serviceNameWithoutSanitization(nussknackerInstanceName, slug))

  private[manager] def serviceNameWithoutSanitization(nussknackerInstanceName: Option[String], slug: String): String =
    objectNamePrefixedWithNussknackerInstanceNameWithoutSanitization(nussknackerInstanceName, slug)

}
