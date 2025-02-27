package pl.touk.nussknacker.engine.embedded

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.data.Validated.Valid
import pl.touk.nussknacker.engine.CustomProcessValidator
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, RequestResponseMetaData}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.SpecificDataValidationError
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.embedded.requestresponse.{RequestResponseDeploymentStrategy, UrlUtils}

object EmbeddedRequestResponseScenarioValidator extends CustomProcessValidator {

  override def validate(scenario: CanonicalProcess): ValidatedNel[ProcessCompilationError, Unit] = {
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

  private def validateRequestResponse(
      scenarioName: ProcessName,
      rrMetaData: RequestResponseMetaData
  ): ValidatedNel[ProcessCompilationError, Unit] = {
    val slug             = RequestResponseDeploymentStrategy.determineSlug(scenarioName, rrMetaData)
    val withSanitization = UrlUtils.sanitizeUrlSlug(slug)
    Validated.cond(withSanitization == slug, (), NonEmptyList.of(IllegalRequestResponseSlug(slug)))
  }

}

object IllegalRequestResponseSlug {

  def apply(slug: String): ProcessCompilationError = {
    SpecificDataValidationError(
      ParameterName(RequestResponseMetaData.slugName),
      s"Illegal slug: $slug. Slug should contain only unreserved url path characters: ${UrlUtils.unreservedUrlCharactersRegex}"
    )
  }

}
