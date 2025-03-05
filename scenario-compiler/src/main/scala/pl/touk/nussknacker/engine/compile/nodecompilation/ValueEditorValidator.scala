package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.Validated.{invalidNel, Valid}
import cats.data.ValidatedNel
import cats.implicits.toTraverseOps
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.definition.{
  DictParameterEditor,
  DualParameterEditor,
  FixedExpressionValue,
  FixedValuesParameterEditor,
  ParameterEditor
}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.parameter.{
  ParameterValueInput,
  ValueInputWithDictEditor,
  ValueInputWithFixedValuesProvided
}
import pl.touk.nussknacker.engine.api.parameter.ParameterName

object ValueEditorValidator {

  // This method doesn't validate the compilation validity of FixedExpressionValues
  //  (it requires validationContext and expressionCompiler, see FragmentParameterValidator.validateFixedExpressionValues)
  // It also doesn't validate in ValueInputWithDictEditor that `dictId` is a declared dictionary and of a correct type
  //  (it requires declared dictionaries, see FragmentParameterValidator.validateValueInputWithDictEditor)
  def validateAndGetEditor(
      valueEditor: ParameterValueInput,
      initialValue: Option[FixedExpressionValue],
      paramName: ParameterName,
      nodeIds: Set[String]
  ): ValidatedNel[PartSubGraphCompilationError, ParameterEditor] = {
    val validatedInnerEditor = valueEditor match {
      case ValueInputWithFixedValuesProvided(fixedValuesList, allowOtherValue) =>
        validateFixedValuesList(fixedValuesList, allowOtherValue, initialValue, paramName, nodeIds)
          .andThen { _ =>
            Valid(FixedValuesParameterEditor(FixedExpressionValue.nullFixedValue +: fixedValuesList))
          }
      case ValueInputWithDictEditor(dictId, _) => Valid(DictParameterEditor(dictId))
    }

    validatedInnerEditor.map { innerEditor =>
      if (valueEditor.allowOtherValue)
        DualParameterEditor(innerEditor, DualEditorMode.SIMPLE)
      else
        innerEditor
    }
  }

  private def validateFixedValuesList(
      fixedValuesList: List[FixedExpressionValue],
      allowOtherValue: Boolean,
      initialValue: Option[FixedExpressionValue],
      paramName: ParameterName,
      nodeIds: Set[String]
  ): ValidatedNel[PartSubGraphCompilationError, Unit] =
    if (!allowOtherValue) {
      List(
        if (fixedValuesList.isEmpty)
          invalidNel(EmptyFixedListForRequiredField(paramName, nodeIds))
        else Valid(()),
        if (initialValueNotPresentInPossibleValues(fixedValuesList, initialValue))
          invalidNel(InitialValueNotPresentInPossibleValues(paramName, nodeIds))
        else Valid(())
      ).sequence.map(_ => ())
    } else { Valid(()) }

  private def initialValueNotPresentInPossibleValues(
      fixedValuesList: List[FixedExpressionValue],
      initialValue: Option[FixedExpressionValue]
  ) = initialValue.exists(!fixedValuesList.contains(_))

}
