package pl.touk.nussknacker.k8s.manager

import cats.data.Validated.Valid
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import pl.touk.nussknacker.engine.CustomProcessValidator
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.SpecificDataValidationError
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{LiteStreamMetaData, RequestResponseMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.k8s.manager.service.ServicePreparer

class LiteScenarioValidator(nussknackerInstanceName: Option[String]) extends CustomProcessValidator {

  def validate(scenario: CanonicalProcess): ValidatedNel[ProcessCompilationError, Unit] = {
    scenario.metaData.typeSpecificData match {
      case _: LiteStreamMetaData =>
        Valid(())
      case rrMetaData: RequestResponseMetaData =>
        validateRequestResponse(ProcessName(scenario.id), rrMetaData)
      case other =>
        throw new IllegalArgumentException("Not supported scenario meta data type: " + other)
    }
  }

  private[manager] def validateRequestResponse(scenarioName: ProcessName, rrMetaData: RequestResponseMetaData): ValidatedNel[ProcessCompilationError, Unit] = {
    val slug = RequestResponseSlugUtils.determineSlug(scenarioName, rrMetaData, nussknackerInstanceName)
    // We don't sanitize / validate against url because k8s object names are more restrictively validated than urls, see https://datatracker.ietf.org/doc/html/rfc3986
    val withoutSanitization = ServicePreparer.serviceNameWithoutSanitization(nussknackerInstanceName, slug)
    val withSanitization = ServicePreparer.serviceName(nussknackerInstanceName, slug)
    val prefix = K8sDeploymentManager.nussknackerInstanceNamePrefix(nussknackerInstanceName)
    Validated.cond(withSanitization == withoutSanitization, (), NonEmptyList.of(SpecificDataValidationError("slug", "Allowed characters include lowercase letters, digits, hyphen, " +
              s"name must start and end alphanumeric character, total length ${if (prefix.isEmpty) s"(including prefix '$prefix') " else ""}cannot be more than ${K8sUtils.maxObjectNameLength}")))
  }

}

object LiteScenarioValidator {

  def apply(config: K8sDeploymentManagerConfig) = new LiteScenarioValidator(config.nussknackerInstanceName)

}
