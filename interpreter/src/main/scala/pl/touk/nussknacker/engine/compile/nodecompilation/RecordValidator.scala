package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.Validated.{Invalid, valid}
import cats.data.ValidatedNel
import cats.implicits.{catsSyntaxTuple2Semigroupal, toFoldableOps}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomParameterValidationError
import pl.touk.nussknacker.engine.api.definition.MandatoryParameterValidator
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.compile.nodecompilation.ParameterValidatorAdapter.validate
import pl.touk.nussknacker.engine.graph.node.{recordKeyFieldName, recordValueFieldName}
import pl.touk.nussknacker.engine.graph.variable.Field

object RecordValidator {

  final case class TypedField(field: Field, index: Int, typingResult: Option[TypingResult])

  def validateRecord(
      fields: List[TypedField]
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit] = {
    fields.map { field =>
      (
        validate(
          field.field.expression,
          field.typingResult,
          recordValueFieldName(field.index),
          MandatoryParameterValidator
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

}
