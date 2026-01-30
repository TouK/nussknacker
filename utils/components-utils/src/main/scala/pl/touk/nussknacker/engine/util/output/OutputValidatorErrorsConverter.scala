package pl.touk.nussknacker.engine.util.output

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.util.Implicits.RichIterable
import pl.touk.nussknacker.engine.util.output.OutputErrors.OutputValidatorGroupTypeError
import pl.touk.nussknacker.engine.util.output.OutputValidatorErrorsMessageFormatter.TypesSeparator

class OutputValidatorErrorsConverter(schemaParamName: ParameterName) {

  import OutputValidatorErrorsMessageFormatter._

  final def convertValidationErrors(
      errors: NonEmptyList[OutputValidatorError],
      detailedDescriptions: Boolean
  )(implicit nodeId: NodeId): CustomNodeError = {
    val outputErrors = OutputErrors.from(errors)
    val message      = makeMessage(outputErrors, detailedDescriptions)
    CustomNodeError(message, Option(schemaParamName))
  }

}

trait OutputValidatorExpected {
  def expected: String
}

final case class OutputErrors private (
    typeErrors: List[OutputValidatorTypeError],
    valueErrors: List[OutputValidatorValueError],
    rangeTypeErrors: List[OutputValidatorRangeTypeError],
    missingFieldsErrors: List[OutputValidatorMissingFieldsError],
    redundantFieldsErrors: List[OutputValidatorRedundantFieldsError]
) {

  def missingFields: List[String] = missingFieldsErrors.flatMap(_.fields)

  def redundantFields: List[String] = redundantFieldsErrors.flatMap(_.fields)

  def typeFieldsErrors(detailed: Boolean): List[String] = {
    typeErrors
      .orderedGroupBy(err => (err.field, err.actual))
      .map { case ((field, actual), errors) =>
        OutputValidatorGroupTypeError(field, actual, errors.map(_.expected).distinct)
      }
      .map { err: OutputValidatorGroupTypeError =>
        s"${withPathDescription(detailed, err.field)}actual: '${err.displayActual}' expected: '${err.displayExpected}'"
      }
  }

  def valueFieldsError(detailed: Boolean): List[String] = {
    valueErrors.map { err =>
      s"${withPathDescription(detailed, err.field)}actual value: '${err.actual.valueOpt.orNull}' should be ${err.expected.expected}"
    }
  }

  def rangeFieldsErrors(detailed: Boolean): List[String] = {
    rangeTypeErrors.map { err =>
      s"${withPathDescription(detailed, err.field)}actual value: '${err.actual.valueOpt.orNull}' should be ${err.expected.expected}"
    }
  }

  private def withPathDescription(detailed: Boolean, field: Option[String]): String = {
    Option.when(detailed)(field.map(f => s"path '$f' ")).flatten.getOrElse("")
  }

  private def addTypeError(e: OutputValidatorTypeError): OutputErrors = {
    this.copy(typeErrors = e :: typeErrors)
  }

  private def addRangeTypeError(e: OutputValidatorRangeTypeError): OutputErrors = {
    this.copy(rangeTypeErrors = e :: rangeTypeErrors)
  }

  private def addValueError(e: OutputValidatorValueError): OutputErrors = {
    this.copy(valueErrors = e :: valueErrors)
  }

  private def addMissingFieldsError(e: OutputValidatorMissingFieldsError): OutputErrors = {
    this.copy(missingFieldsErrors = e :: missingFieldsErrors)
  }

  private def addRedundantFieldsError(e: OutputValidatorRedundantFieldsError): OutputErrors = {
    this.copy(redundantFieldsErrors = e :: redundantFieldsErrors)
  }

}

object OutputErrors {

  def from(errors: NonEmptyList[OutputValidatorError]): OutputErrors = {
    errors.toList.reverse.foldLeft(OutputErrors(Nil, Nil, Nil, Nil, Nil)) { (acc, error) =>
      error match {
        case e: OutputValidatorTypeError            => acc.addTypeError(e)
        case e: OutputValidatorValueError           => acc.addValueError(e)
        case e: OutputValidatorRangeTypeError       => acc.addRangeTypeError(e)
        case e: OutputValidatorMissingFieldsError   => acc.addMissingFieldsError(e)
        case e: OutputValidatorRedundantFieldsError => acc.addRedundantFieldsError(e)
      }
    }
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

sealed trait OutputValidatorError

case class OutputValidatorTypeError(field: Option[String], actual: TypingResult, expected: OutputValidatorExpected)
    extends OutputValidatorError

case class OutputValidatorValueError(field: Option[String], actual: TypingResult, expected: OutputValidatorExpected)
    extends OutputValidatorError

case class OutputValidatorRangeTypeError(field: Option[String], actual: TypingResult, expected: OutputValidatorExpected)
    extends OutputValidatorError

case class OutputValidatorMissingFieldsError(fields: Set[String]) extends OutputValidatorError

case class OutputValidatorRedundantFieldsError(fields: Set[String]) extends OutputValidatorError
