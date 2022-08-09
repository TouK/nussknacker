package pl.touk.nussknacker.engine

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

//TODO: Move to some designer extensions module or to common extensions module when we switch from CanonicalProcess to DisplayableProcess in all places
trait CustomProcessValidatorFactory {
  def validator(config: Config): CustomProcessValidator
}

trait CustomProcessValidator {
  def validate(process: CanonicalProcess): List[ProcessCompilationError]
}