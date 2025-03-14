package pl.touk.nussknacker.engine.definition.component.parameter.editor

import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor.{
  ParameterEditor => AnnotationParameterEditor,
  ParameterEditorType => AnnotationParameterEditorType
}
import pl.touk.nussknacker.engine.api.parameter.{
  ParameterValueInput,
  ValueInputWithDictEditor,
  ValueInputWithFixedValuesProvided
}
import pl.touk.nussknacker.engine.definition.component.parameter.ParameterData

object EditorExtractor {

  def extract(valueInput: ParameterValueInput): ParameterEditors = {
    val innerEditor = valueInput match {
      case ValueInputWithFixedValuesProvided(fixedValuesList, _) =>
        FixedValuesParameterEditor(FixedExpressionValue.nullFixedValue +: fixedValuesList)
      case ValueInputWithDictEditor(dictId, _) =>
        DictParameterEditor(dictId)
    }

    if (valueInput.allowOtherValue)
      ParameterEditors(
        innerEditor,
        SpelParameterEditor,
      )
    else
      ParameterEditors(innerEditor)
  }

  def extract(param: ParameterData, parameterConfig: ParameterConfig): Option[ParameterEditors] = {
    parameterConfig.parsedEditors
      .orElse(extractFromAnnotations(param))
      .orElse(new ParameterTypeEditorDeterminer(param.typing).determine())
  }

  private def extractFromAnnotations(param: ParameterData): Option[ParameterEditors] = {
    // FIXME lbg
    ???
//    val simpleEditorAnnotation: Option[ParameterEditor] = param.getAnnotation[ParameterEditor]
//    val rawEditorAnnotation: Option[SpelEditor]      = param.getAnnotation[SpelEditor]
//
//    (simpleEditorAnnotation, rawEditorAnnotation) match {
//      case (Some(simpleEditorAnnotation: ParameterEditor), Some(_: SpelEditor)) =>
//        if (simpleEditorAnnotation.isMainEditor)
//          ParameterEditors(simpleParameterEditor(simpleEditorAnnotation), SpelParameterEditor).some
//        else ParameterEditors(SpelParameterEditor, simpleParameterEditor(simpleEditorAnnotation)).some
//      case (Some(simpleEditorAnnotation: ParameterEditor), None) =>
//        ParameterEditors(simpleParameterEditor(simpleEditorAnnotation)).some
//      case (None, Some(_: SpelEditor)) => ParameterEditors(SpelParameterEditor).some
//      case _                           => None
//    }
  }

  private def parameterEditor(simpleEditor: AnnotationParameterEditor): ParameterEditor = {
    simpleEditor.`type`() match {
      case AnnotationParameterEditorType.BOOL_EDITOR => BoolParameterEditor
      case AnnotationParameterEditorType.FIXED_VALUES_EDITOR =>
        FixedValuesParameterEditor(
          simpleEditor
            .possibleValues()
            .map(value => FixedExpressionValue(value.expression(), value.label()))
            .toList
        )
      case AnnotationParameterEditorType.DATE_EDITOR      => DateParameterEditor
      case AnnotationParameterEditorType.TIME_EDITOR      => TimeParameterEditor
      case AnnotationParameterEditorType.DATE_TIME_EDITOR => DateTimeParameterEditor
      case AnnotationParameterEditorType.DURATION_EDITOR =>
        DurationParameterEditor(simpleEditor.timeRangeComponents().toList)
      case AnnotationParameterEditorType.PERIOD_EDITOR =>
        PeriodParameterEditor(simpleEditor.timeRangeComponents().toList)
      case AnnotationParameterEditorType.CRON_EDITOR               => CronParameterEditor
      case AnnotationParameterEditorType.TEXTAREA_EDITOR           => TextareaParameterEditor
      case AnnotationParameterEditorType.JSON_EDITOR               => JsonParameterEditor
      case AnnotationParameterEditorType.SQL_EDITOR                => SqlParameterEditor
      case AnnotationParameterEditorType.SPEL_EDITOR               => SpelParameterEditor
      case AnnotationParameterEditorType.SPEL_TEMPLATE_EDITOR      => SpelTemplateParameterEditor
      case AnnotationParameterEditorType.DICT_EDITOR               => DictParameterEditor(simpleEditor.dictId())
      case AnnotationParameterEditorType.TYPED_TABULAR_DATA_EDITOR => TabularTypedDataEditor
    }
  }

}
