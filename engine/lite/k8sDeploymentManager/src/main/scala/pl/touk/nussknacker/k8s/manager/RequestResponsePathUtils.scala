package pl.touk.nussknacker.k8s.manager

import pl.touk.nussknacker.engine.api.RequestResponseMetaData
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.k8s.manager.service.ServicePreparer.serviceName

object RequestResponsePathUtils {

  private[manager] def determinePath(scenarioName: ProcessName, rrMetaData: RequestResponseMetaData) = {
    rrMetaData.path.getOrElse(scenarioName.value)
  }

  // We don't encode url because k8s object names are more restrictively validated than urls, see https://datatracker.ietf.org/doc/html/rfc3986
  // and all invalid characters will be clean
  private[manager] def defaultPath(scenarioName: ProcessName, nussknackerInstanceName: Option[String]): String = {
    val maxPathLength = K8sUtils.maxObjectNameLength - K8sDeploymentManager.nussknackerInstanceNamePrefix(nussknackerInstanceName).length
    serviceName(None, scenarioName.value).take(maxPathLength)
  }

}
