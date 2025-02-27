package pl.touk.nussknacker.engine.flink.table.sink

import cats.data.Validated.{invalidNel, Valid}
import cats.data.ValidatedNel
import org.apache.flink.table.types.logical.LogicalType
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.table.utils.DataTypesExtensions._
import pl.touk.nussknacker.engine.flink.table.utils.ToTableTypeSchemaBasedEncoder
import pl.touk.nussknacker.engine.util.output.{OutputValidatorError, OutputValidatorExpected, OutputValidatorTypeError}

object TableTypeOutputValidator {

  def validate(actualType: TypingResult, expectedType: LogicalType): ValidatedNel[OutputValidatorError, Unit] = {
    val aligned              = ToTableTypeSchemaBasedEncoder.alignTypingResult(actualType, expectedType)
    val expectedTypingResult = expectedType.toTypingResult

    if (aligned.canBeConvertedTo(expectedTypingResult)) {
      Valid(())
    } else {
      invalidNel(
        OutputValidatorTypeError(
          None,
          actualType,
          new OutputValidatorExpected {
            // Maybe we should use LogicalType.toString instead?
            override def expected: String = expectedTypingResult.display
          }
        )
      )
    }
  }

}
