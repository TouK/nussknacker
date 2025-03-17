package pl.touk.nussknacker.engine.definition.component.parameter.editor

import cats.implicits.catsSyntaxOptionId
import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor._
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
      .orElse(extractFromAnnotation(param))
      .orElse(extractFromAnnotations(param))
      .orElse(new ParameterTypeEditorDeterminer(param.typing).determine())
  }

  private def extractFromAnnotation(param: ParameterData): Option[ParameterEditors] =
    param
      .getAnnotation[Editor]
      .map(editor => ParameterEditors(parameterEditor(editor)))

  private def extractFromAnnotations(param: ParameterData): Option[ParameterEditors] =
    param
      .getAnnotation[Editors]
      .flatMap(editors =>
        editors.value().toList match {
          case first :: Nil =>
            ParameterEditors(parameterEditor(first)).some
          case first :: second :: Nil =>
            (parameterEditor(first), parameterEditor(second)) match {
              case (simple: SimpleParameterEditor, spel: SpelParameterEditor.type) =>
                if (first.isMainEditor) ParameterEditors(simple, spel).some
                else ParameterEditors(spel, simple).some
              case (spel: SpelParameterEditor.type, simple: SimpleParameterEditor) =>
                if (first.isMainEditor) ParameterEditors(spel, simple).some
                else ParameterEditors(simple, spel).some
              case _ =>
                throw new IllegalStateException(
                  s"Configuration of ${first.`type`()} and ${second.`type`()} is not allowed"
                )
            }
          case _ => None
        }
      )

  private def parameterEditor(simpleEditor: Editor): ParameterEditor = {
    simpleEditor.`type`() match {
      case EditorType.BOOL_EDITOR => BoolParameterEditor
      case EditorType.FIXED_VALUES_EDITOR =>
        FixedValuesParameterEditor(
          simpleEditor
            .possibleValues()
            .map(value => FixedExpressionValue(value.expression(), value.label()))
            .toList
        )
      case EditorType.DATE_EDITOR               => DateParameterEditor
      case EditorType.TIME_EDITOR               => TimeParameterEditor
      case EditorType.DATE_TIME_EDITOR          => DateTimeParameterEditor
      case EditorType.DURATION_EDITOR           => DurationParameterEditor(simpleEditor.timeRangeComponents().toList)
      case EditorType.PERIOD_EDITOR             => PeriodParameterEditor(simpleEditor.timeRangeComponents().toList)
      case EditorType.CRON_EDITOR               => CronParameterEditor
      case EditorType.TEXTAREA_EDITOR           => TextareaParameterEditor
      case EditorType.JSON_EDITOR               => JsonParameterEditor
      case EditorType.SQL_EDITOR                => SqlParameterEditor
      case EditorType.SPEL_TEMPLATE_EDITOR      => SpelTemplateParameterEditor
      case EditorType.DICT_EDITOR               => DictParameterEditor(simpleEditor.dictId())
      case EditorType.TYPED_TABULAR_DATA_EDITOR => TabularTypedDataEditor
      case EditorType.SPEL_EDITOR               => SpelParameterEditor
    }
  }

}
