package pl.touk.nussknacker.k8s.manager.service

import pl.touk.nussknacker.engine.api.{LiteStreamMetaData, ProcessVersion, RequestResponseMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.k8s.manager.K8sDeploymentManager.{labelsForScenario, nussknackerInstanceNameLabel, objectNameForScenario, objectNamePrefixedWithNussknackerInstanceName, scenarioIdLabel, scenarioVersionAnnotation}
import pl.touk.nussknacker.k8s.manager.K8sDeploymentManagerConfig
import skuber.{ObjectMeta, Service}
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.k8s.manager.K8sUtils.sanitizeObjectName
import pl.touk.nussknacker.k8s.manager.service.ServicePreparer.{defaultServiceName, runtimePodTargetPort, serviceName}
import skuber.Service.Port

class ServicePreparer(config: K8sDeploymentManagerConfig) {

  def prepare(processVersion: ProcessVersion, scenario: CanonicalProcess): Option[Service] = {
    scenario.metaData.typeSpecificData match {
      case _: LiteStreamMetaData =>
        None
      case rrMetaData: RequestResponseMetaData =>
        Some(prepareRequestResponseService(processVersion, rrMetaData))
      case other =>
        throw new IllegalArgumentException("Not supported scenario meta data type: " + other)
    }
  }

  private def prepareRequestResponseService(processVersion: ProcessVersion, rrMetaData: RequestResponseMetaData): Service = {
    val objectName = serviceName(config.nussknackerInstanceName, processVersion, rrMetaData)
    val annotations = Map(scenarioVersionAnnotation -> processVersion.asJson.spaces2)
    val labels = labelsForScenario(processVersion, config.nussknackerInstanceName)
    val selectors = Map(
      //here we use id to avoid sanitization problems
      scenarioIdLabel -> processVersion.processId.value.toString) ++ config.nussknackerInstanceName.map(nussknackerInstanceNameLabel -> _)

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

  private[manager] def serviceName(nussknackerInstanceName: Option[String], processVersion: ProcessVersion, rrMetaData: RequestResponseMetaData) = {
    rrMetaData.path
      .map(objectNamePrefixedWithNussknackerInstanceName(nussknackerInstanceName, _)) // TODO: path should be required and validated (sanitization shouldn't change anything)
      .getOrElse(defaultServiceName(nussknackerInstanceName, processVersion.processName))
  }

  private[manager] def defaultServiceName(nussknackerInstanceName: Option[String], scenarioName: ProcessName): String =
    objectNamePrefixedWithNussknackerInstanceName(nussknackerInstanceName, scenarioName.value)

}
