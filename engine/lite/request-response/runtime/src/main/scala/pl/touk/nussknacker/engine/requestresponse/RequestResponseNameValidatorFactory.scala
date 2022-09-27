package pl.touk.nussknacker.engine.requestresponse

import cats.data.Validated
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.RequestResponseMetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.SpecificDataValidationError
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.requestresponse.RequestResponseNameValidatorFactory.{maxSize, regexpMatches}
import pl.touk.nussknacker.engine.{CustomProcessValidator, CustomProcessValidatorFactory}

import java.util.regex.Pattern

object RequestResponseNameValidatorFactory {
  //https://kubernetes.io/docs/concepts/overview/working-with-objects/names/#rfc-1035-label-names
  private val regexpMatches = Pattern.compile("^[a-z][a-z0-9\\-]*[a-z0-9]$").asMatchPredicate()

  private val maxSize = 63
}

class RequestResponseNameValidatorFactory extends CustomProcessValidatorFactory {
  override def validator(config: Config): CustomProcessValidator = (process: CanonicalProcess) => {
    process.metaData.typeSpecificData match {
      case RequestResponseMetaData(Some(slug)) if !regexpMatches.test(slug) || slug.length > maxSize =>
        Validated.invalidNel(SpecificDataValidationError("slug", "Allowed characters include lowercase letters, digits, hyphen, " +
          "name must start and end alphanumeric character"))
      case _ => Validated.valid(())
    }
  }
}
