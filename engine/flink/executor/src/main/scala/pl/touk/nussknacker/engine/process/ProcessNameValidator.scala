package pl.touk.nussknacker.engine.process

import pl.touk.nussknacker.engine.CustomProcessValidator
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

import scala.concurrent.Future

object ProcessNameValidator extends CustomProcessValidator {

  private lazy val flinkProcessNameValidationPattern = "[a-zA-Z0-9-_ ]+"r

  def validate(process: CanonicalProcess): List[ProcessCompilationError] = {

    val processName = process.metaData.id
    if (flinkProcessNameValidationPattern.pattern.matcher(processName).matches()) {
      List()
    } else {
      List(
//        new IllegalStateException(
//          s"Illegal characters in process name: $processName. Allowed characters include numbers letters, underscores(_), hyphens(-) and spaces"
//        )

      )
    }

  }

}