package pl.touk.nussknacker.engine.definition

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{
  InitialValueNotPresentInPossibleValues,
  RequireValueFromUndefinedFixedList
}
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentParameter, FragmentParameterInputMode}

object FragmentParameterValidator {

  def validate(
      fragmentParameterWithEffectivePresets: FragmentParameter,
      fragmentInputId: String
  ): List[ProcessCompilationError] = {
    import fragmentParameterWithEffectivePresets._

    val allowOnlyValuesFromFixedValuesList =
      fragmentParameterWithEffectivePresets.inputMode == FragmentParameterInputMode.InputModeFixedList

    List(
      (allowOnlyValuesFromFixedValuesList && effectiveFixedValuesList.isEmpty)
        -> RequireValueFromUndefinedFixedList(name, Set(fragmentInputId)),
      (initialValue.isDefined && allowOnlyValuesFromFixedValuesList && !effectiveFixedValuesList.contains(initialValue))
        -> InitialValueNotPresentInPossibleValues(name, Set(fragmentInputId))
    ).collect {
      case (condition, error) if condition => error
    }
  }

}
