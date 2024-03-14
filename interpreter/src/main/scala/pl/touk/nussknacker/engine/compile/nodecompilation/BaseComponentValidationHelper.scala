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
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.compile._
import pl.touk.nussknacker.engine.compiledgraph.TypedParameter
import pl.touk.nussknacker.engine.expression.parse.TypedExpression

object BaseComponentValidationHelper {

  def validateBoolean(
      expression: ValidatedNel[ProcessCompilationError, TypedExpression],
      paramName: ParameterName
  )(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, Unit] = {
    validateOrValid(NotNullParameterValidator, expression, paramName)
  }

  def validateVariableValue(
      expression: ValidatedNel[ProcessCompilationError, TypedExpression],
      paramName: ParameterName
  )(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, Unit] = {
    validateOrValid(MandatoryParameterValidator, expression, paramName)
  }

  private def validateOrValid(
      validator: ParameterValidator,
      expression: ValidatedNel[ProcessCompilationError, TypedExpression],
      paramName: ParameterName
  )(implicit nodeId: NodeId) = {
    expression
      .map { expr =>
        Validations
          .validate(List(validator), TypedParameter(paramName, expr))
          .map(_ => ())
      }
      .getOrElse(valid(()))
  }

}
