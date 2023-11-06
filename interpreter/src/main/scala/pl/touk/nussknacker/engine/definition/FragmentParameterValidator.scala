package pl.touk.nussknacker.engine.definition

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{
  InitialValueNotPresentInPossibleValues,
  RequireValueFromEmptyFixedList
}
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{
  FragmentParameter,
  FragmentParameterFixedValuesUserDefinedList,
  FragmentParameterInputMode,
  FragmentParameterNoFixedValues
}

object FragmentParameterValidator {

  def validate(
      fragmentParameter: FragmentParameter,
      fragmentInputId: String,
      compiler: NodeCompiler,
      validationContext: ValidationContext
  )(implicit nodeId: NodeId): List[ProcessCompilationError] = {
    val expectedType = compiler.loadFromParameter(fragmentParameter)
    val fixedValuesList = fragmentParameter match {
      case _: FragmentParameterNoFixedValues              => None
      case f: FragmentParameterFixedValuesUserDefinedList => Some(f.fixedValuesList)
    }

    val fixedValueResponses = (fixedValuesList.getOrElse(List.empty) ++ fragmentParameter.initialValue).map {
      fixedExpressionValue =>
        compiler.compileExpression( // todo make sure this doesn't break on lazy values (like #input.something)
          Expression.spel(fixedExpressionValue.expression),
          validationContext,
          expectedType = expectedType,
          outputVar = None
        ) // TODO change to a better/clearer error, currently:
        // NodeValidationError("ExpressionParserCompilationError",
        // "Failed to parse expression: Bad expression type, expected: Boolean, found: String(someValue)",
        // "There is problem with expression in field Some($expression) - it could not be parsed.",
        // Some("$expression"), SaveAllowed),
    }

    val allowOnlyValuesFromFixedList =
      fragmentParameter.inputMode == FragmentParameterInputMode.InputModeFixedList

    List(
      (allowOnlyValuesFromFixedList && fixedValuesList.isEmpty)
        -> RequireValueFromEmptyFixedList(fragmentParameter.name, Set(fragmentInputId)),
      ((allowOnlyValuesFromFixedList, fragmentParameter.initialValue, fixedValuesList) match {
        case (true, Some(value), Some(fixedValuesList)) if !fixedValuesList.contains(value) => true
        case _                                                                              => false
      }) -> InitialValueNotPresentInPossibleValues(fragmentParameter.name, Set(fragmentInputId)),
    ).collect {
      case (condition, error) if condition => error
    } ++ fixedValueResponses.flatMap(_.errors)
  }

}
