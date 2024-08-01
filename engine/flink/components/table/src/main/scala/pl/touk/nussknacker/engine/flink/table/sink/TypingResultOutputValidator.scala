package pl.touk.nussknacker.engine.flink.table.sink

import cats.data.Validated.{Valid, invalidNel}
import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.typed.CanBeSubclassDeterminer
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.util.output.{OutputValidatorError, OutputValidatorExpected, OutputValidatorTypeError}

object TypingResultOutputValidator {

  private val determinerAcceptingVariousObjTypeForRecord = new CanBeSubclassDeterminer {
    override protected def checkObjTypeForRecord: Boolean = false
  }

  // TODO: make this validation more precise analogous to kafka avro / json
  def validate(actualType: TypingResult, expectedType: TypingResult): ValidatedNel[OutputValidatorError, Unit] = {
    if (determinerAcceptingVariousObjTypeForRecord.canBeSubclassOf(actualType, expectedType).isValid) {
      Valid(())
    } else {
      invalidNel(
        OutputValidatorTypeError(
          None,
          actualType,
          new OutputValidatorExpected {
            override def expected: String = expectedType.display
          }
        )
      )
    }
  }

}
