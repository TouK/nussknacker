package pl.touk.nussknacker.engine.util.output

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.typed.typing.{TypedUnion, TypingResult}

object OutputValidatorErrorsConverter {

  implicit class DisplayingTypingResult(typingResult: TypingResult) {
    def displayType: String = typingResult match {
      case un: TypedUnion if un.isEmptyUnion => "null" //Is it okey? Null is presented as EmptyUnion...
      case _ => typingResult.display.capitalize
    }
  }

}

class OutputValidatorErrorsConverter(schemaParamName: String) {

  import OutputValidatorErrorsMessageFormatter._

  final def convertValidationErrors(errors: NonEmptyList[OutputValidatorError])(implicit nodeId: NodeId): CustomNodeError = {
    val missingFieldsError = errors.collect { case e: OutputValidatorMissingFieldsError => e }.flatMap(_.fields)
    val redundantFieldsError = errors.collect { case e: OutputValidatorRedundantFieldsError => e }.flatMap(_.fields)
    val typeFieldsError = errors
      .collect { case e: OutputValidatorTypeError => e }
      .groupBy(err => (err.field, err.actual))
      .map { case ((field, actual), errors) =>
        OutputValidatorGroupTypeError(field, actual, errors.map(_.expected).distinct)
      }.toList

    val messageTypeFieldErrors = typeFieldsError.map(err =>
      s"path '${err.field}' actual: '${err.displayActual}' expected: '${err.displayExpected}'"
    )

    val message = makeMessage(messageTypeFieldErrors, missingFieldsError, redundantFieldsError)
    CustomNodeError(message, Option(schemaParamName))
  }

  case class OutputValidatorGroupTypeError(field: String, actual: TypingResult, expected: List[OutputValidatorExpected]) {
    import OutputValidatorErrorsConverter._

    def displayExpected: String = expected.map(_.expected).mkString(TypesSeparator)
    def displayActual: String = actual.displayType
  }

}

trait OutputValidatorExpected {
  def expected: String
}

sealed trait OutputValidatorError

case class OutputValidatorTypeError(field: String, actual: TypingResult, expected: OutputValidatorExpected) extends OutputValidatorError

case class OutputValidatorMissingFieldsError(fields: Set[String]) extends OutputValidatorError

case class OutputValidatorRedundantFieldsError(fields: Set[String]) extends OutputValidatorError
