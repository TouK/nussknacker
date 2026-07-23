package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.data.Validated.valid
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
import pl.touk.nussknacker.engine.expression.parse.{SingleBranchTypedValue, TypedExpression}

object BaseComponentValidationHelper {

  def validateBoolean(
      expression: ValidatedNel[ProcessCompilationError, TypedExpression],
      paramName: ParameterName,
      inputContext: SingleInputNodeInputValidationContext
  )(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, Unit] = {
    validateOrValid(NotNullParameterValidator, expression, paramName, inputContext)
  }

  def validateVariableValue(
      expression: ValidatedNel[ProcessCompilationError, TypedExpression],
      paramName: ParameterName,
      inputContext: SingleInputNodeInputValidationContext
  )(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, Unit] = {
    validateOrValid(MandatoryParameterValidator, expression, paramName, inputContext)
  }

  private def validateOrValid(
      validator: ParameterValidator,
      expression: ValidatedNel[ProcessCompilationError, TypedExpression],
      paramName: ParameterName,
      inputContext: SingleInputNodeInputValidationContext
  )(implicit nodeId: NodeId): Validated[NonEmptyList[PartSubGraphCompilationError], Unit] = {
    expression
      .map { expr =>
        CompileTimeParameterValidation
          .validate(
            List(validator),
            TypedParameter(paramName, SingleBranchTypedValue(expr, inputContext.validationContext)),
            evaluatedResult = None
          )
          .map(_ => ())
      }
      .getOrElse(valid(()))
  }

}
