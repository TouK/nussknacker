package pl.touk.nussknacker.engine.util.parameters

import cats.data.{Validated, ValidatedNel}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.util.output.OutputValidatorError

trait TypingResultValidator {
  def validate(typingResult: TypingResult): ValidatedNel[OutputValidatorError, Unit]
}

object TypingResultValidator {
  val emptyValidator: TypingResultValidator = (_: TypingResult) => Validated.Valid((): Unit)
}
