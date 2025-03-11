package pl.touk.nussknacker.engine.definition.component.parameter.editor

import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor._
import pl.touk.nussknacker.engine.api.parameter.{
  ParameterValueInput,
  ValueInputWithDictEditor,
  ValueInputWithFixedValuesProvided
}
import pl.touk.nussknacker.engine.definition.component.parameter.ParameterData
import pl.touk.nussknacker.engine.util.Implicits.RichIterable

object EditorExtractor {

  def extract(valueInput: ParameterValueInput): List[ParameterEditor] = {
    val innerEditor = valueInput match {
      case ValueInputWithFixedValuesProvided(fixedValuesList, _) =>
        FixedValuesParameterEditor(FixedExpressionValue.nullFixedValue +: fixedValuesList)
      case ValueInputWithDictEditor(dictId, _) =>
        DictParameterEditor(dictId)
    }

    if (valueInput.allowOtherValue)
      List(
        innerEditor,
        SpelParameterEditor,
      )
    else
      List(innerEditor)
  }

  def extract(param: ParameterData, parameterConfig: ParameterConfig): List[ParameterEditor] = {
    parameterConfig.editors
      .getOrElse(Nil)
      .orElseIfEmpty(extractFromAnnotations(param))
      .orElseIfEmpty(new ParameterTypeEditorDeterminer(param.typing).determine())
  }

  private def extractFromAnnotations(param: ParameterData): List[ParameterEditor] = {
    val simpleEditorAnnotation: Option[SimpleEditor] = param.getAnnotation[SimpleEditor]
    val rawEditorAnnotation: Option[SpelEditor]      = param.getAnnotation[SpelEditor]

    (simpleEditorAnnotation, rawEditorAnnotation) match {
      case (Some(simpleEditorAnnotation: SimpleEditor), Some(_: SpelEditor)) =>
        if (simpleEditorAnnotation.isDefaultEditor)
          List(simpleParameterEditor(simpleEditorAnnotation), SpelParameterEditor)
        else List(SpelParameterEditor, simpleParameterEditor(simpleEditorAnnotation))
      case (Some(simpleEditorAnnotation: SimpleEditor), None) =>
        List(simpleParameterEditor(simpleEditorAnnotation))
      case (None, Some(_: SpelEditor)) => List(SpelParameterEditor)
      case _                           => Nil
    }
  }

  private def simpleParameterEditor(simpleEditor: SimpleEditor): SimpleParameterEditor = {
    simpleEditor.`type`() match {
      case SimpleEditorType.BOOL_EDITOR => BoolParameterEditor
      case SimpleEditorType.FIXED_VALUES_EDITOR =>
        FixedValuesParameterEditor(
          simpleEditor
            .possibleValues()
            .map(value => FixedExpressionValue(value.expression(), value.label()))
            .toList
        )
      case SimpleEditorType.DATE_EDITOR          => DateParameterEditor
      case SimpleEditorType.TIME_EDITOR          => TimeParameterEditor
      case SimpleEditorType.DATE_TIME_EDITOR     => DateTimeParameterEditor
      case SimpleEditorType.DURATION_EDITOR      => DurationParameterEditor(simpleEditor.timeRangeComponents().toList)
      case SimpleEditorType.PERIOD_EDITOR        => PeriodParameterEditor(simpleEditor.timeRangeComponents().toList)
      case SimpleEditorType.CRON_EDITOR          => CronParameterEditor
      case SimpleEditorType.TEXTAREA_EDITOR      => TextareaParameterEditor
      case SimpleEditorType.JSON_EDITOR          => JsonParameterEditor
      case SimpleEditorType.SQL_EDITOR           => SqlParameterEditor
      case SimpleEditorType.SPEL_TEMPLATE_EDITOR => SpelTemplateParameterEditor
      case SimpleEditorType.DICT_EDITOR          => DictParameterEditor(simpleEditor.dictId())
      case SimpleEditorType.TYPED_TABULAR_DATA_EDITOR => TabularTypedDataEditor
    }
  }

}
