package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.Validated.{Valid, invalidNel}
import cats.data.ValidatedNel
import cats.implicits.toTraverseOps
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.definition.{
  DualParameterEditor,
  FixedExpressionValue,
  FixedValuesParameterEditor,
  ParameterEditor
}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.parameter.{ParameterName, ValueInputWithFixedValues}

object ValueEditorValidator {

  def validateAndGetEditor( // this method doesn't validate the compilation validity of FixedExpressionValues (it requires validationContext and expressionCompiler, see FragmentParameterValidator.validateFixedExpressionValues)
      valueEditor: ValueInputWithFixedValues,
      initialValue: Option[FixedExpressionValue],
      paramName: String,
      nodeIds: Set[String]
  ): ValidatedNel[PartSubGraphCompilationError, ParameterEditor] = {
    validateFixedValuesList(valueEditor, initialValue, paramName, nodeIds)
      .andThen { _ =>
        val fixedValuesEditor = FixedValuesParameterEditor(
          FixedExpressionValue.nullFixedValue +: valueEditor.fixedValuesList
        )

        if (valueEditor.allowOtherValue) {
          Valid(DualParameterEditor(fixedValuesEditor, DualEditorMode.SIMPLE))
        } else {
          Valid(fixedValuesEditor)
        }
      }
  }

  private def validateFixedValuesList(
      valueEditor: ValueInputWithFixedValues,
      initialValue: Option[FixedExpressionValue],
      paramName: String,
      nodeIds: Set[String]
  ): ValidatedNel[PartSubGraphCompilationError, Unit] =
    if (!valueEditor.allowOtherValue) {
      List(
        if (valueEditor.fixedValuesList.isEmpty)
          invalidNel(RequireValueFromEmptyFixedList(ParameterName(paramName), nodeIds))
        else Valid(()),
        if (initialValueNotPresentInPossibleValues(valueEditor, initialValue))
          invalidNel(InitialValueNotPresentInPossibleValues(ParameterName(paramName), nodeIds))
        else Valid(())
      ).sequence.map(_ => ())
    } else { Valid(()) }

  private def initialValueNotPresentInPossibleValues(
      valueEditor: ValueInputWithFixedValues,
      initialValue: Option[FixedExpressionValue]
  ) = initialValue.exists(!valueEditor.fixedValuesList.contains(_))

}
