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
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, ValueInputWithFixedValues}

object ValueEditorValidator {

  def validateAndGetEditor( // this method doesn't validate the compilation validity of FixedExpressionValues (it requires validationContext and expressionCompiler, see FragmentParameterValidator.validateFixedExpressionValues)
      valueEditor: ValueInputWithFixedValues,
      initialValue: Option[FixedExpressionValue],
      refClazzName: Option[String],
      paramName: String,
      nodeIds: Set[String]
  ): ValidatedNel[PartSubGraphCompilationError, ParameterEditor] = {
    refClazzName
      .map(validateFixedValuesSupportedType(_, paramName, nodeIds))
      .getOrElse(Valid(()))
      .andThen(_ => validateFixedValuesList(valueEditor, initialValue, paramName, nodeIds))
      .andThen { _ =>
        val fixedValuesEditor = FixedValuesParameterEditor(
          nullFixedValue +: valueEditor.fixedValuesList.map(v => FixedExpressionValue(v.expression, v.label))
        )

        if (valueEditor.allowOtherValue) {
          Valid(DualParameterEditor(fixedValuesEditor, DualEditorMode.SIMPLE))
        } else {
          Valid(fixedValuesEditor)
        }
      }
  }

  private val nullFixedValue: FixedExpressionValue = FixedExpressionValue("", "")

  private def validateFixedValuesSupportedType(
      refClazzName: String,
      paramName: String,
      nodeIds: Set[String]
  ): ValidatedNel[PartSubGraphCompilationError, Unit] =
    if (List(FragmentClazzRef[java.lang.Boolean].refClazzName, FragmentClazzRef[String].refClazzName)
        .contains(refClazzName)) {
      Valid(())
    } else
      invalidNel(
        UnsupportedFixedValuesType(
          paramName,
          refClazzName,
          nodeIds
        )
      )

  private def validateFixedValuesList(
      valueEditor: ValueInputWithFixedValues,
      initialValue: Option[FixedExpressionValue],
      paramName: String,
      nodeIds: Set[String]
  ): ValidatedNel[PartSubGraphCompilationError, Unit] =
    if (!valueEditor.allowOtherValue) {
      List(
        if (valueEditor.fixedValuesList.isEmpty)
          invalidNel(RequireValueFromEmptyFixedList(paramName, nodeIds))
        else Valid(()),
        if (initialValueNotPresentInPossibleValues(valueEditor, initialValue))
          invalidNel(InitialValueNotPresentInPossibleValues(paramName, nodeIds))
        else Valid(())
      ).sequence.map(_ => ())
    } else { Valid(()) }

  private def initialValueNotPresentInPossibleValues(
      valueEditor: ValueInputWithFixedValues,
      initialValue: Option[FixedExpressionValue]
  ) = initialValue.exists(!valueEditor.fixedValuesList.contains(_))

}
