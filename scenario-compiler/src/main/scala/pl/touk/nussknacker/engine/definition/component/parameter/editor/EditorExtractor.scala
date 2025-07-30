package pl.touk.nussknacker.engine.definition.component.parameter.editor

import pl.touk.nussknacker.engine.ModelConfig.GlobalParametersConfig
import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor._
import pl.touk.nussknacker.engine.definition.component.parameter.ParameterData
import pl.touk.nussknacker.engine.util.Implicits.RichIterable

object EditorExtractor {

  def extract(
      param: ParameterData,
      parameterConfig: ParameterConfig,
      globalParametersConfig: GlobalParametersConfig
  ): List[ParameterEditor] = {
    parameterConfig.editors
      .getOrElse(Nil)
      .orElseIfEmpty(extractFromAnnotation(param))
      .orElseIfEmpty(extractFromAnnotations(param))
      .orElseIfEmpty(new ParameterTypeEditorDeterminer(param.typing, globalParametersConfig).determine())
  }

  private def extractFromAnnotation(param: ParameterData): List[ParameterEditor] =
    param
      .getAnnotation[Editor]
      .map(editor => List(parameterEditor(editor)))
      .getOrElse(Nil)

  private def extractFromAnnotations(param: ParameterData): List[ParameterEditor] =
    param
      .getAnnotation[Editors]
      .map(_.value().map(parameterEditor).toList)
      .getOrElse(Nil)

  private def parameterEditor(editor: Editor): ParameterEditor = {
    editor.`type`() match {
      case EditorType.BOOL_EDITOR => BoolParameterEditor
      case EditorType.FIXED_VALUES_EDITOR =>
        FixedValuesParameterEditor(
          editor
            .possibleValues()
            .map(value => FixedExpressionValue(value.expression(), value.label()))
            .toList
        )
      case EditorType.DATE_EDITOR               => DateParameterEditor
      case EditorType.TIME_EDITOR               => TimeParameterEditor
      case EditorType.DATE_TIME_EDITOR          => DateTimeParameterEditor
      case EditorType.DURATION_EDITOR           => DurationParameterEditor(editor.timeRangeComponents().toList)
      case EditorType.PERIOD_EDITOR             => PeriodParameterEditor(editor.timeRangeComponents().toList)
      case EditorType.CRON_EDITOR               => CronParameterEditor
      case EditorType.TEXTAREA_EDITOR           => TextareaParameterEditor
      case EditorType.JSON_EDITOR               => JsonParameterEditor
      case EditorType.SQL_EDITOR                => SqlParameterEditor
      case EditorType.SPEL_TEMPLATE_EDITOR      => SpelTemplateParameterEditor
      case EditorType.DICT_EDITOR               => DictParameterEditor(editor.dictId())
      case EditorType.TYPED_TABULAR_DATA_EDITOR => TabularTypedDataEditor
      case EditorType.SPEL_EDITOR               => SpelParameterEditor
      case EditorType.JSON_TEMPLATE_EDITOR      => JsonTemplateParameterEditor
    }
  }

}
