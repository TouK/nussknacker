package pl.touk.nussknacker.engine

import cats.data.ValidatedNel
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

trait CustomProcessValidatorFactory {
  def validator(config: Config): CustomProcessValidator
}

trait CustomProcessValidator {
  def validate(process: CanonicalProcess): ValidatedNel[ProcessCompilationError, Unit]
}
