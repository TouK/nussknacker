package pl.touk.nussknacker.k8s.manager

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.data.Validated.Valid
import pl.touk.nussknacker.engine.CustomProcessValidator
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, RequestResponseMetaData}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.SpecificDataValidationError
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.k8s.manager.service.ServicePreparer

class RequestResponseScenarioValidator(nussknackerInstanceName: Option[String]) extends CustomProcessValidator {

  def validate(scenario: CanonicalProcess): ValidatedNel[ProcessCompilationError, Unit] = {
    scenario.metaData.typeSpecificData match {
      case rrMetaData: RequestResponseMetaData =>
        validateRequestResponse(scenario.name, rrMetaData)
      case _: FragmentSpecificData =>
        Valid(())
      // should not happen
      case other =>
        throw new IllegalArgumentException("This validator supports only Request-Response mode, got: " + other)
    }
  }

  private[manager] def validateRequestResponse(
      scenarioName: ProcessName,
      rrMetaData: RequestResponseMetaData
  ): ValidatedNel[ProcessCompilationError, Unit] = {
    val slug = RequestResponseSlugUtils.determineSlug(scenarioName, rrMetaData, nussknackerInstanceName)
    // We don't sanitize / validate against url because k8s object names are more restrictively validated than urls, see https://datatracker.ietf.org/doc/html/rfc3986
    val withoutSanitization = ServicePreparer.serviceNameWithoutSanitization(nussknackerInstanceName, slug)
    val withSanitization    = ServicePreparer.serviceName(nussknackerInstanceName, slug)
    val prefix              = K8sDeploymentManager.nussknackerInstanceNamePrefix(nussknackerInstanceName)
    Validated.cond(
      withSanitization == withoutSanitization,
      (),
      NonEmptyList.of(
        SpecificDataValidationError(
          ParameterName(RequestResponseMetaData.slugName),
          "Allowed characters include lowercase letters, digits, hyphen, " +
            s"name must start and end alphanumeric character, total length ${if (prefix.isEmpty) s"(including prefix '$prefix') "
              else ""}cannot be more than ${K8sUtils.maxObjectNameLength}"
        )
      )
    )
  }

}

object RequestResponseScenarioValidator {

  def apply(config: K8sDeploymentManagerConfig) = new RequestResponseScenarioValidator(config.nussknackerInstanceName)

}
