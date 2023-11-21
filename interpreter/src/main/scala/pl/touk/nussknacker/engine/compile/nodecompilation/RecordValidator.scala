package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.Validated.{Invalid, valid}
import cats.data.ValidatedNel
import cats.implicits.{catsSyntaxTuple2Semigroupal, toFoldableOps}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomParameterValidationError
import pl.touk.nussknacker.engine.api.definition.{MandatoryParameterValidator, Parameter}
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.compile.Validations
import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam.TypedParameter
import pl.touk.nussknacker.engine.graph.node.{recordKeyFieldName, recordValueFieldName}

object RecordValidator {

  final case class TypedField(field: TypedParameter, index: Int)

  def validateRecord(
      fields: List[TypedField]
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit] = {
    fields.map { field =>
      (
        validate(
          Parameter("stub", Unknown, List(MandatoryParameterValidator)),
          field.field.copy(name = recordValueFieldName(field.index))
        ),
        validateUniqueRecordKey(fields.map(_.field.name), field.field.name, recordKeyFieldName(field.index))
      ).mapN { (_, _) => () }
    }.combineAll
  }

  private def validate(paramDefinition: Parameter, parameter: TypedParameter)(implicit nodeId: NodeId) = {
    Validations.validate(paramDefinition, (parameter, ())).map(_ => ())
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
