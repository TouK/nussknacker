package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.Validated.{valid, Invalid, Valid}
import cats.data.ValidatedNel
import cats.implicits.{catsSyntaxTuple2Semigroupal, toFoldableOps}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.{PartSubGraphCompilationError, ProcessCompilationError}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomParameterValidationError
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.compile.nodecompilation.BaseComponentValidationHelper.validateVariableValue
import pl.touk.nussknacker.engine.compiledgraph.variable.Field
import pl.touk.nussknacker.engine.expression.parse.TypedExpression
import pl.touk.nussknacker.engine.graph.node.{recordKeyFieldName, recordValueFieldName}

final case class CompiledIndexedRecordField(
    field: Field,
    index: Int,
    typedExpression: TypedExpression
)

final case class IndexedRecordKey(key: String, index: Int)

object RecordValidator {

  def validate(
      compiledRecord: ValidatedNel[PartSubGraphCompilationError, List[CompiledIndexedRecordField]],
      indexedFields: List[IndexedRecordKey]
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit] = {
    val emptyValuesResult = compiledRecord match {
      case Valid(fields) => validateRecordEmptyValues(fields)
      case Invalid(_)    => valid(())
    }
    val uniqueKeysResult = validateUniqueKeys(indexedFields)
    (emptyValuesResult, uniqueKeysResult).mapN((_, _) => ())
  }

  private def validateRecordEmptyValues(
      fields: List[CompiledIndexedRecordField]
  )(implicit nodeId: NodeId) = {
    fields.map { field =>
      validateVariableValue(Valid(field.typedExpression), ParameterName(recordValueFieldName(field.index)))
    }.combineAll
  }

  private def validateUniqueKeys(
      fields: List[IndexedRecordKey]
  )(implicit nodeId: NodeId) = {
    fields.map { field =>
      validateUniqueRecordKey(fields.map(_.key), field.key, recordKeyFieldName(field.index))
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
          CustomParameterValidationError(
            "The key of a record has to be unique",
            "Record key not unique",
            ParameterName(fieldName),
            nodeId.id
          )
        )
      }.toValidatedNel
  }

}
