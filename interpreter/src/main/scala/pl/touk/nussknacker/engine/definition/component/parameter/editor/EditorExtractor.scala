package pl.touk.nussknacker.engine.definition.component.parameter.editor

import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, RawEditor, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.definition.component.parameter.ParameterData
import FixedExpressionValue.nullFixedValue
import pl.touk.nussknacker.engine.api.parameter.{ValueInputWithFixedValues, ValueInputWithFixedValuesProvided}

object EditorExtractor {

  def extract(valueInput: ValueInputWithFixedValues): ParameterEditor = valueInput match {
    case ValueInputWithFixedValuesProvided(fixedValuesList, allowOtherValue) =>
      val fixedValuesEditor = FixedValuesParameterEditor(
        nullFixedValue +: fixedValuesList
      )

      if (allowOtherValue) {
        DualParameterEditor(fixedValuesEditor, DualEditorMode.SIMPLE)
      } else {
        fixedValuesEditor
      }
  }

  def extract(param: ParameterData, parameterConfig: ParameterConfig): Option[ParameterEditor] = {
    parameterConfig.editor
      .orElse(extractFromAnnotations(param))
      .orElse(new ParameterTypeEditorDeterminer(param.typing).determine())
  }

  private def extractFromAnnotations(param: ParameterData): Option[ParameterEditor] = {
    val dualEditorAnnotation: Option[DualEditor]     = param.getAnnotation[DualEditor]
    val simpleEditorAnnotation: Option[SimpleEditor] = param.getAnnotation[SimpleEditor]
    val rawEditorAnnotation: Option[RawEditor]       = param.getAnnotation[RawEditor]

    (dualEditorAnnotation, simpleEditorAnnotation, rawEditorAnnotation) match {
      case (Some(dualEditorAnnotation: DualEditor), None, None) => {
        val defaultMode  = dualEditorAnnotation.defaultMode()
        val simpleEditor = dualEditorAnnotation.simpleEditor()
        Some(DualParameterEditor(simpleParameterEditor(simpleEditor), defaultMode))
      }
      case (None, Some(simpleEditorAnnotation: SimpleEditor), None) =>
        Some(simpleParameterEditor(simpleEditorAnnotation))
      case (None, None, Some(_: RawEditor)) => Some(RawParameterEditor)
      case _                                => None
    }
  }

  private def simpleParameterEditor(simpleEditor: SimpleEditor): SimpleParameterEditor = {
    simpleEditor.`type`() match {
      case SimpleEditorType.BOOL_EDITOR   => BoolParameterEditor
      case SimpleEditorType.STRING_EDITOR => StringParameterEditor
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
    }
  }

}
