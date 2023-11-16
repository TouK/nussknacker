package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.Validated.{Invalid, valid}
import cats.data.ValidatedNel
import cats.implicits.{catsSyntaxTuple2Semigroupal, catsSyntaxValidatedId, toFoldableOps}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomParameterValidationError
import pl.touk.nussknacker.engine.api.definition.{
  MandatoryParameterValidator,
  NotBlankParameterValidator,
  NotNullParameterValidator,
  ParameterValidator
}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.NodeExpressionId.DefaultExpressionId
import pl.touk.nussknacker.engine.graph.node.{Filter, Variable, recordKeyFieldName, recordValueFieldName}
import pl.touk.nussknacker.engine.graph.variable.Field

object BaseComponentsValidator {

  def validate(filter: Filter)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit] = {
    validateSequentially(
      filter.expression.expression,
      DefaultExpressionId,
      List(MandatoryParameterValidator, NotBlankParameterValidator, NotNullParameterValidator)
    )
  }

  def validate(variable: Variable)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit] = {
    validateSequentially(
      variable.value.expression,
      DefaultExpressionId,
      List(MandatoryParameterValidator, NotBlankParameterValidator)
    )
  }

  def validateRecord(fields: List[Field])(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit] = {
    fields.zipWithIndex.map { case (field, index) =>
      (
        validate(field.expression.expression, recordValueFieldName(index), MandatoryParameterValidator),
        validateUniqueRecordKey(fields.map(_.name), field.name, recordKeyFieldName(index)).toValidatedNel
      ).mapN { (_, _) => () }
    }.combineAll
  }

  def validateSwitchChoices(
      choices: List[(String, Expression)]
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit] = {
    choices.map { case (branchFieldName, expression) =>
      validateSequentially(
        expression.expression,
        branchFieldName,
        List(MandatoryParameterValidator, NotBlankParameterValidator, NotNullParameterValidator)
      )
    }.combineAll
  }

  private def validateSequentially(expression: String, fieldName: String, validators: List[ParameterValidator])(
      implicit nodeId: NodeId
  ) = {
    validators.foldLeft(().validNel[ProcessCompilationError]) { (acc, validator) =>
      acc.andThen(_ => validate(expression, fieldName, validator))
    }
  }

  private def validate(value: String, fieldName: String, validator: ParameterValidator)(
      implicit nodeId: NodeId
  ) = {
    validator.isValid(fieldName, Expression.spel(value), value, None).toValidatedNel
  }

  private def validateUniqueRecordKey(allValues: Seq[String], requiredToBeUniqueValue: String, fieldName: String)(
      implicit nodeId: NodeId
  ) = {
    if (allValues.count(_ == requiredToBeUniqueValue) <= 1) {
      valid(())
    } else {
      Invalid(
        // TODO: Make typed Error for this?
        CustomParameterValidationError(
          "The key of a record has to be unique",
          "Record key not unique",
          fieldName,
          nodeId.id
        )
      )
    }
  }

}
