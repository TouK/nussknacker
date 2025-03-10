package pl.touk.nussknacker.k8s.manager.service

import pl.touk.nussknacker.engine.api.{LiteStreamMetaData, MetaData, ProcessVersion, RequestResponseMetaData}
import pl.touk.nussknacker.k8s.manager.{K8sDeploymentManagerConfig, K8sUtils, OptionalNussknackerInstanceName}
import pl.touk.nussknacker.k8s.manager.K8sDeploymentManager._
import pl.touk.nussknacker.k8s.manager.K8sUtils.sanitizeObjectName
import pl.touk.nussknacker.k8s.manager.RequestResponseSlugUtils.determineSlug
import pl.touk.nussknacker.k8s.manager.service.ServicePreparer.runtimePodTargetPort
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
    val objectName = ServicePreparer.serviceName(
      config.nussknackerInstanceName,
      determineSlug(processVersion.processName, rrMetaData, config.nussknackerInstanceName)
    )
    val annotations = versionAnnotationForScenario(processVersion)
    val labels      = labelsForScenario(processVersion, config.nussknackerInstanceName)
    val selectors = Map(
      // here we use id to avoid sanitization problems
      scenarioIdLabel -> processVersion.processId.value.toString
    ) ++ config.nussknackerInstanceName.valueOpt.map(nussknackerInstanceNameLabel -> _)

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

  private[manager] def serviceName(nussknackerInstanceName: OptionalNussknackerInstanceName, slug: String): String =
    K8sUtils.sanitizeObjectName(serviceNameWithoutSanitization(nussknackerInstanceName, slug))

  private[manager] def serviceNameWithoutSanitization(
      nussknackerInstanceName: OptionalNussknackerInstanceName,
      slug: String
  ): String =
    nussknackerInstanceName.objectNameWithoutSanitization(slug)

}
