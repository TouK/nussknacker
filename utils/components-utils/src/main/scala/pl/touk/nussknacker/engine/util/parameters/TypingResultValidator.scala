package pl.touk.nussknacker.engine.util.parameters

import pl.touk.nussknacker.engine.util.output.OutputValidatorError
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import cats.data.{Validated, ValidatedNel}

trait TypingResultValidator {
  def validate(typingResult: TypingResult): ValidatedNel[OutputValidatorError, Unit]
}

object TypingResultValidator {
  val emptyValidator: TypingResultValidator = (_: TypingResult) => Validated.Valid((): Unit)
}
