package pl.touk.nussknacker.engine.compile.nodecompilation

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{
  InitialValueNotPresentInPossibleValues,
  InvalidParameterInputConfig,
  RequireValueFromEmptyFixedList
}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{
  FixedExpressionValue,
  FragmentParameter,
  FragmentParameterInputMode
}

object FragmentParameterValidator {

  def validate(
      fragmentParameter: FragmentParameter,
      fragmentInputId: String,
      compiler: NodeCompiler,
      validationContext: ValidationContext // localVariables must include this and other FragmentParameters
  )(implicit nodeId: NodeId): List[ProcessCompilationError] = {
    val inputConfigResponse = if (fragmentParameter.inputConfig.isValid) {
      List.empty
    } else {
      List(InvalidParameterInputConfig(fragmentParameter.name, Set(fragmentInputId)))
    }

    val fixedExpressionsResponses = validateFixedExpressions(
      fragmentParameter.inputConfig.fixedValuesList.getOrElse(List.empty) ++ fragmentParameter.initialValue,
      compiler,
      validationContext,
      fragmentParameter.name
    )

    val fixedValuesListResponses = validateFixedValuesList(fragmentParameter, fragmentInputId)

    inputConfigResponse ++ fixedValuesListResponses ++ fixedExpressionsResponses
  }

  private def validateFixedExpressions(
      fixedValues: List[FixedExpressionValue],
      compiler: NodeCompiler,
      validationContext: ValidationContext,
      paramName: String
  )(implicit nodeId: NodeId) = fixedValues
    .map { fixedExpressionValue =>
      compiler.compileExpression(
        expr = Expression.spel(fixedExpressionValue.expression),
        ctx = validationContext,
        expectedType = validationContext(paramName),
        fieldName = paramName,
        outputVar = None
      )
    }
    .flatMap(_.errors)

  private def validateFixedValuesList(fragmentParameter: FragmentParameter, fragmentInputId: String) =
    if (fragmentParameter.inputConfig.inputMode == FragmentParameterInputMode.InputModeFixedList) {
      List(
        fragmentParameter.inputConfig.fixedValuesList.isEmpty
          -> RequireValueFromEmptyFixedList(fragmentParameter.name, Set(fragmentInputId)),
        ((fragmentParameter.initialValue, fragmentParameter.inputConfig.fixedValuesList) match {
          case (Some(value), Some(fixedValuesList)) if !fixedValuesList.contains(value) => true
          case _                                                                        => false
        }) -> InitialValueNotPresentInPossibleValues(fragmentParameter.name, Set(fragmentInputId)),
      ).collect {
        case (condition, error) if condition => error
      }
    } else {
      List.empty
    }

}
