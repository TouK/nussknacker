package pl.touk.nussknacker.engine.definition

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{
  InitialValueNotPresentInPossibleValues,
  RequireValueFromUndefinedFixedList
}
import pl.touk.nussknacker.engine.api.fixedvaluespresets.FixedValuesPresetProvider
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.FragmentParameter

object FragmentParameterValidator {

  def validate(
      fragmentParameterWithSubstitutedPresets: FragmentParameter,
      fragmentInputId: String
  ): List[ProcessCompilationError] = {
    import fragmentParameterWithSubstitutedPresets._

    List(
      (allowOnlyValuesFromFixedValuesList && fixedValueList.isEmpty)
        -> RequireValueFromUndefinedFixedList(name, Set(fragmentInputId)),
      (initialValue.isDefined && allowOnlyValuesFromFixedValuesList && !fixedValueList.contains(initialValue))
        -> InitialValueNotPresentInPossibleValues(name, Set(fragmentInputId))

      // TODO ? fixedValuesList defined and fixedValuesPresetId undefined -> `typ` must be string or boolean ???
      // TODO ? (harder) initialValue (and fixedValues?) have to be of proper type (subclass/castable to `typ`) ?
    ).collect {
      case (condition, error) if condition => error
    }
  }

}
