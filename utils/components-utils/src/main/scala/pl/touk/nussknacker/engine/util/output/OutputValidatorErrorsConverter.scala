package pl.touk.nussknacker.engine.util.output

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

class OutputValidatorErrorsConverter(schemaParamName: ParameterName) {

  import OutputValidatorErrorsMessageFormatter._

  final def convertValidationErrors(
      errors: NonEmptyList[OutputValidatorError]
  )(implicit nodeId: NodeId): CustomNodeError = {
    val missingFieldsError   = errors.collect { case e: OutputValidatorMissingFieldsError => e }.flatMap(_.fields)
    val redundantFieldsError = errors.collect { case e: OutputValidatorRedundantFieldsError => e }.flatMap(_.fields)
    val typeFieldsError = errors
      .collect { case e: OutputValidatorTypeError => e }
      .groupBy(err => (err.field, err.actual))
      .map { case ((field, actual), errors) =>
        OutputValidatorGroupTypeError(field, actual, errors.map(_.expected).distinct)
      }
      .toList

    val messageTypeFieldErrors = typeFieldsError.map(err =>
      s"${err.field.map(f => s"path '$f' ").getOrElse("")}actual: '${err.displayActual}' expected: '${err.displayExpected}'"
    )

    val fieldsRangeError = errors
      .collect { case e: OutputValidatorRangeTypeError => e }
      .map(err =>
        s"${err.field.map(f => s"path '$f' ").getOrElse("")}actual value: '${err.actual.valueOpt.orNull}' should be ${err.expected.expected}"
      )

    val message = makeMessage(messageTypeFieldErrors, missingFieldsError, redundantFieldsError, fieldsRangeError)
    CustomNodeError(message, Option(schemaParamName))
  }

  case class OutputValidatorGroupTypeError(
      field: Option[String],
      actual: TypingResult,
      expected: List[OutputValidatorExpected]
  ) {
    def displayExpected: String = expected.map(_.expected).mkString(TypesSeparator)
    def displayActual: String   = actual.display
  }

}

trait OutputValidatorExpected {
  def expected: String
}

sealed trait OutputValidatorError

case class OutputValidatorRangeTypeError(field: Option[String], actual: TypingResult, expected: OutputValidatorExpected)
    extends OutputValidatorError

case class OutputValidatorTypeError(field: Option[String], actual: TypingResult, expected: OutputValidatorExpected)
    extends OutputValidatorError

case class OutputValidatorMissingFieldsError(fields: Set[String]) extends OutputValidatorError

case class OutputValidatorRedundantFieldsError(fields: Set[String]) extends OutputValidatorError
