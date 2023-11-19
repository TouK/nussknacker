package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.Validated.{Invalid, valid}
import cats.data.ValidatedNel
import cats.implicits.{catsSyntaxTuple2Semigroupal, catsSyntaxValidatedId, toFoldableOps}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomParameterValidationError
import pl.touk.nussknacker.engine.api.definition.{MandatoryParameterValidator, ParameterValidator}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.{recordKeyFieldName, recordValueFieldName}
import pl.touk.nussknacker.engine.graph.variable.Field

object BaseComponentsValidator {

  final case class TypedField(field: Field, index: Int, typingResult: Option[TypingResult])

  def validateFailFast(
      expression: Expression,
      typingResult: Option[TypingResult],
      fieldName: String,
      validators: List[ParameterValidator]
  )(
      implicit nodeId: NodeId
  ): ValidatedNel[ProcessCompilationError, Unit] = {
    validators.foldLeft(().validNel[ProcessCompilationError]) { (acc, validator) =>
      acc.andThen(_ => validator.isValid(fieldName, expression, extractValue(typingResult), None).toValidatedNel)
    }
  }

  def validateRecord(
      fields: List[TypedField]
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit] = {
    fields.map { field =>
      (
        validateFailFast(
          field.field.expression,
          field.typingResult,
          recordValueFieldName(field.index),
          List(MandatoryParameterValidator)
        ),
        validateUniqueRecordKey(fields.map(_.field.name), field.field.name, recordKeyFieldName(field.index))
      ).mapN { (_, _) => () }
    }.combineAll
  }

  private def validateUniqueRecordKey(allValues: Seq[String], requiredToBeUniqueValue: String, fieldName: String)(
      implicit nodeId: NodeId
  ) = {
    if (allValues.count(_ == requiredToBeUniqueValue) <= 1) {
      valid(())
    } else
      {
        Invalid(
          // TODO: Make typed Error for this?
          CustomParameterValidationError(
            "The key of a record has to be unique",
            "Record key not unique",
            fieldName,
            nodeId.id
          )
        )
      }.toValidatedNel
  }

  private def extractValue(typingResult: Option[TypingResult]) = typingResult match {
    case Some(value) =>
      value.valueOpt match {
        case Some(value) => value
        case None        => null
      }
    case None => null
  }

}
