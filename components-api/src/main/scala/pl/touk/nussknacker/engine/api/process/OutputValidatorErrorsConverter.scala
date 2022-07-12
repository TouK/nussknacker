package pl.touk.nussknacker.engine.api.process

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
    val accumulator = (List.empty[OutputValidatorMissingFieldsError], List.empty[OutputValidatorRedundantFieldsError], List.empty[OutputValidatorTypeError])
    val (missingFieldsError, redundantFieldsError, typeFieldsError) = errors.toList.foldRight(accumulator) {
      case (e: OutputValidatorMissingFieldsError, (missingFieldsError, redundantFieldsError, typeFieldsError)) =>
        (List(e) ::: missingFieldsError, redundantFieldsError, typeFieldsError)
      case (e: OutputValidatorRedundantFieldsError, (missingFieldsError, redundantFieldsError, typeFieldsError)) =>
        (missingFieldsError, List(e) ::: redundantFieldsError, typeFieldsError)
      case (e: OutputValidatorTypeError, (missingFieldsError, redundantFieldsError, typeFieldsError)) =>
        (missingFieldsError, redundantFieldsError, List(e) ::: typeFieldsError)
    }

    val groupedTypeFieldsError = typeFieldsError
      .groupBy(err => (err.field, err.actual))
      .map { case ((field, actual), errors) =>
        OutputValidatorGroupTypeError(field, actual, errors.map(_.expected).distinct)
      }.toList

    val messageTypeFieldErrors = groupedTypeFieldsError.map(err =>
      s"path '${err.field}' actual: '${err.displayActual}' expected: '${err.displayExpected}'"
    )

    val message = makeMessage(
      messageTypeFieldErrors,
      missingFieldsError.flatMap(_.fields),
      redundantFieldsError.flatMap(_.fields)
    )

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
