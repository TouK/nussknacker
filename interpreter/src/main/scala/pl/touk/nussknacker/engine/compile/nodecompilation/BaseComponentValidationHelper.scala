package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.Validated.valid
import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.definition.{
  MandatoryParameterValidator,
  NotNullParameterValidator,
  ParameterValidator
}
import pl.touk.nussknacker.engine.api.expression.TypedExpression
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.compile._
import pl.touk.nussknacker.engine.compiledgraph.TypedParameter

object BaseComponentValidationHelper {

  def validateBoolean(
      expression: ValidatedNel[ProcessCompilationError, TypedExpression],
      fieldName: String
  )(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, Unit] = {
    validateOrValid(NotNullParameterValidator, expression, fieldName)
  }

  def validateVariableValue(
      expression: ValidatedNel[ProcessCompilationError, TypedExpression],
      fieldName: String
  )(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, Unit] = {
    validateOrValid(MandatoryParameterValidator, expression, fieldName)
  }

  private def validateOrValid(
      validator: ParameterValidator,
      expression: ValidatedNel[ProcessCompilationError, TypedExpression],
      fieldName: String
  )(implicit nodeId: NodeId) = {
    expression
      .map { expr =>
        Validations
          .validate(List(validator), TypedParameter(ParameterName(fieldName), expr))
          .map(_ => ())
      }
      .getOrElse(valid(()))
  }

}
