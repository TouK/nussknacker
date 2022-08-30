package pl.touk.nussknacker.k8s.manager

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{LiteStreamMetaData, RequestResponseMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.k8s.manager.service.ServicePreparer

class LiteScenarioValidator(nussknackerInstanceName: Option[String]) {

  def validate(scenario: CanonicalProcess): Validated[Throwable, Unit] = {
    scenario.metaData.typeSpecificData match {
      case _: LiteStreamMetaData =>
        Valid(())
      case rrMetaData: RequestResponseMetaData =>
        validateRequestResponse(ProcessName(scenario.id), rrMetaData)
      case other =>
        throw new IllegalArgumentException("Not supported scenario meta data type: " + other)
    }
  }

  private[manager] def validateRequestResponse(scenarioName: ProcessName, rrMetaData: RequestResponseMetaData): Validated[Throwable, Unit] = {
    val path = RequestResponsePathUtils.determinePath(scenarioName, rrMetaData)
    // We don't sanitize / validate against url because k8s object names are more restrictively validated than urls, see https://datatracker.ietf.org/doc/html/rfc3986
    val withoutSanitization = ServicePreparer.serviceNameWithoutSanitization(nussknackerInstanceName, path)
    val withSanitization = ServicePreparer.serviceName(nussknackerInstanceName, path)
    Validated.cond(withSanitization == withoutSanitization, (), IllegalRequestResponsePath(nussknackerInstanceName, path))
  }

}

object LiteScenarioValidator {

  def apply(config: K8sDeploymentManagerConfig) = new LiteScenarioValidator(config.nussknackerInstanceName)

}

case class IllegalRequestResponsePath(nussknackerInstanceName: Option[String], path: String)
  extends RuntimeException(s"Illegal path: $path. Path ${nussknackerInstanceName.map(i => s"after prefixation by instance name: '$i-' ").getOrElse("")}should match url path pattern and kubernetes object name pattern")