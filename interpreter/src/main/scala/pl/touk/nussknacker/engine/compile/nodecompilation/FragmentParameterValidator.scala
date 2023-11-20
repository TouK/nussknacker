package pl.touk.nussknacker.engine.compile.nodecompilation

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{
  ExpressionParserCompilationError,
  ExpressionParserCompilationErrorInFragmentDefinition,
  InitialValueNotPresentInPossibleValues,
  MissingFixedValuesList,
  RequireValueFromEmptyFixedList,
  UnsupportedFixedValuesType
}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.ParameterInputMode.{
  InputModeAny,
  InputModeAnyWithSuggestions,
  InputModeFixedList
}
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{
  FixedExpressionValue,
  FragmentClazzRef,
  FragmentParameter,
  ParameterInputMode
}
import pl.touk.nussknacker.engine.graph.node.{FixedValuesListFieldName, InitialValueFieldName}

object FragmentParameterValidator {

  def validate(
      fragmentParameter: FragmentParameter,
      fragmentInputId: String,
      compiler: NodeCompiler,
      validationContext: ValidationContext // localVariables must include this and other FragmentParameters
  )(implicit nodeId: NodeId): List[ProcessCompilationError] = {
    val inputConfigResponse = validateInputConfig(fragmentParameter, fragmentInputId)

    val fixedExpressionsResponses = validateFixedExpressionValues(
      fragmentParameter.initialValue,
      fragmentParameter.inputConfig.effectiveFixedValuesList.getOrElse(List.empty),
      compiler,
      validationContext,
      fragmentParameter.name
    )

    val fixedValuesListResponses = validateFixedValuesList(fragmentParameter, fragmentInputId)

    inputConfigResponse ++ fixedValuesListResponses ++ fixedExpressionsResponses
  }

  private def validateInputConfig(fragmentParameter: FragmentParameter, fragmentInputId: String) =
    fragmentParameter.inputConfig.inputMode match {
      case InputModeAny => List.empty
      case InputModeAnyWithSuggestions | InputModeFixedList =>
        val missingFixedValuesResponse = fragmentParameter.inputConfig.fixedValuesList match {
          case Some(_) => List.empty
          case None    => List(MissingFixedValuesList(fragmentParameter.name, Set(fragmentInputId)))
        }

        val unsupportedFixedValuesTypeResponse =
          if (!List(FragmentClazzRef[Boolean], FragmentClazzRef[String]).contains(fragmentParameter.typ))
            List(
              UnsupportedFixedValuesType(
                fragmentParameter.name,
                fragmentParameter.typ.refClazzName,
                Set(fragmentInputId)
              )
            )
          else
            List.empty

        missingFixedValuesResponse ++ unsupportedFixedValuesTypeResponse
    }

  private def validateFixedExpressionValues(
      initialValue: Option[FixedExpressionValue],
      fixedValuesList: List[FixedExpressionValue],
      compiler: NodeCompiler,
      validationContext: ValidationContext,
      paramName: String
  )(implicit nodeId: NodeId) = {
    def fixedExpressionsCompilationErrors(
        fixedExpressions: Iterable[FixedExpressionValue],
        subFieldName: Option[String],
    ) = fixedExpressions
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
      .map {
        case e: ExpressionParserCompilationError =>
          ExpressionParserCompilationErrorInFragmentDefinition(
            e.message,
            nodeId.id,
            paramName,
            subFieldName,
            e.originalExpr
          )
        case e => e
      }

    fixedExpressionsCompilationErrors(
      initialValue,
      Some(InitialValueFieldName)
    ) ++ fixedExpressionsCompilationErrors(
      fixedValuesList,
      Some(FixedValuesListFieldName)
    )
  }

  private def validateFixedValuesList(fragmentParameter: FragmentParameter, fragmentInputId: String) =
    if (fragmentParameter.inputConfig.inputMode == ParameterInputMode.InputModeFixedList) {
      List(
        fragmentParameter.inputConfig.effectiveFixedValuesList.isEmpty
          -> RequireValueFromEmptyFixedList(fragmentParameter.name, Set(fragmentInputId)),
        ((fragmentParameter.initialValue, fragmentParameter.inputConfig.effectiveFixedValuesList) match {
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
