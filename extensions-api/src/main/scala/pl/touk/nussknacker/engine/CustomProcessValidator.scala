package pl.touk.nussknacker.engine

import cats.data.ValidatedNel
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

// TODO: We should remove this extension mechanism - we already have these validators exposed by
//       DeploymentManagerProvider.additionalValidators. We don't need to keep this logic on the runtime side
trait CustomProcessValidatorFactory {
  def validator(config: Config): CustomProcessValidator
}

trait CustomProcessValidator {
  def validate(process: CanonicalProcess): ValidatedNel[ProcessCompilationError, Unit]
}
