package pl.touk.nussknacker.engine.definition.component.parameter.editor

import cats.data.NonEmptyList
import io.circe.Json
import pl.touk.nussknacker.engine.ModelConfig.GlobalParametersConfig
import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor._
import pl.touk.nussknacker.engine.definition.component.parameter.ParameterData

object EditorExtractor {

  def extract(
      param: ParameterData,
      parameterConfig: ParameterConfig,
      globalParametersConfig: GlobalParametersConfig
  ): NonEmptyList[ParameterEditor] = {
    parameterConfig.editors
      .flatMap(NonEmptyList.fromList)
      .orElse(extractFromAnnotation(param))
      .orElse(extractFromAnnotations(param))
      .getOrElse(
        new ParameterTypeEditorDeterminer(param.typing, param.isLazyParameter, globalParametersConfig).determine()
      )
  }

  private def extractFromAnnotation(param: ParameterData): Option[NonEmptyList[ParameterEditor]] =
    param
      .getAnnotation[Editor]
      .map(editor => NonEmptyList.one(parameterEditor(editor)))

  private def extractFromAnnotations(param: ParameterData): Option[NonEmptyList[ParameterEditor]] =
    param
      .getAnnotation[Editors]
      .flatMap(editors => NonEmptyList.fromList(editors.value().map(parameterEditor).toList))

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
      case EditorType.MULTI_SELECT_EDITOR =>
        MultiSelectEditor(
          editor
            .possibleMultiSelectValues()
            .map(option =>
              MultiSelectFixedValue(
                value = Json.fromString(option.value()),
                label = option.label(),
                description = Option(option.description()).filterNot(_.isBlank),
                group = Option(option.group()).filterNot(_.isBlank),
              )
            )
            .toList
        )
    }
  }

}
