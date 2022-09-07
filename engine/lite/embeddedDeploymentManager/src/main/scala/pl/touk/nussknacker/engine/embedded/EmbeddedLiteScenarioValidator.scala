package pl.touk.nussknacker.engine.embedded

import cats.data.Validated
import cats.data.Validated.Valid
import pl.touk.nussknacker.engine.api.{LiteStreamMetaData, RequestResponseMetaData}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.embedded.requestresponse.{RequestResponseDeploymentStrategy, UrlUtils}

object EmbeddedLiteScenarioValidator {

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

  private def validateRequestResponse(scenarioName: ProcessName, rrMetaData: RequestResponseMetaData): Validated[Throwable, Unit] = {
    val slug = RequestResponseDeploymentStrategy.determineSlug(scenarioName, rrMetaData)
    val withSanitization = UrlUtils.sanitizeUrlSlug(slug)
    Validated.cond(withSanitization == slug, (), IllegalRequestResponseSlug(slug))
  }

}

case class IllegalRequestResponseSlug(slug: String)
  extends RuntimeException(s"Illegal slug: $slug. Slug should contain only unreserved url path characters: ${UrlUtils.unreservedUrlCharactersRegex}")