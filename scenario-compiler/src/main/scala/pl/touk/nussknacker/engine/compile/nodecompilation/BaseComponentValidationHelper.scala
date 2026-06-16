package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.Validated.valid
import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.definition.{MandatoryExpressionValidator, NotNullValidator, ParameterValidator}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.compile._
import pl.touk.nussknacker.engine.compiledgraph.TypedParameter
import pl.touk.nussknacker.engine.expression.parse.{SingleBranchTypedValue, TypedExpression}

object BaseComponentValidationHelper {

  def validateBoolean(
      expression: ValidatedNel[ProcessCompilationError, TypedExpression],
      paramName: ParameterName,
      inputContext: SingleInputNodeInputValidationContext
  )(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, Unit] = {
    validateOrValid(NotNullValidator, expression, paramName, inputContext)
  }

  def validateVariableValue(
      expression: ValidatedNel[ProcessCompilationError, TypedExpression],
      paramName: ParameterName,
      inputContext: SingleInputNodeInputValidationContext
  )(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, Unit] = {
    validateOrValid(MandatoryExpressionValidator, expression, paramName, inputContext)
  }

  private def validateOrValid(
      validator: ParameterValidator,
      expression: ValidatedNel[ProcessCompilationError, TypedExpression],
      paramName: ParameterName,
      inputContext: SingleInputNodeInputValidationContext
  )(implicit nodeId: NodeId) = {
    expression
      .map { expr =>
        Validations
          .validate(
            List(validator),
            TypedParameter(paramName, SingleBranchTypedValue(expr, inputContext.validationContext))
          )
          .map(_ => ())
      }
      .getOrElse(valid(()))
  }

}
