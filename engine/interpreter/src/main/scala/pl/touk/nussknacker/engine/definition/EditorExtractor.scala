package pl.touk.nussknacker.engine.definition

import java.lang.reflect.Parameter

import pl.touk.nussknacker.engine.api.definition.{BoolParameterEditor, CronParameterEditor, DateParameterEditor, DateTimeParameterEditor, DualParameterEditor, DurationParameterEditor, FixedExpressionValue, FixedValuesParameterEditor, ParameterEditor, PeriodParameterEditor, RawParameterEditor, StringParameterEditor, TimeParameterEditor}
import pl.touk.nussknacker.engine.api.editor.{DualEditor, RawEditor, SimpleEditor, SimpleEditorType}


object EditorExtractor {

  def extract(param: Parameter): Option[ParameterEditor] = {
    val dualEditorAnnotation: DualEditor = param.getAnnotation(classOf[DualEditor])
    val simpleEditorAnnotation = param.getAnnotation(classOf[SimpleEditor])
    val rawEditorAnnotation = param.getAnnotation(classOf[RawEditor])

    (dualEditorAnnotation, simpleEditorAnnotation, rawEditorAnnotation) match {
      case (dualEditorAnnotation: DualEditor, null, null) => {
        val defaultMode = dualEditorAnnotation.defaultMode()
        val simpleEditor = dualEditorAnnotation.simpleEditor()
        Some(DualParameterEditor(simpleParameterEditor(simpleEditor), defaultMode))
      }
      case (null, simpleEditorAnnotation: SimpleEditor, null) => Some(simpleParameterEditor(simpleEditorAnnotation))
      case (null, null, rawEditorAnnotation: RawEditor) => Some(RawParameterEditor)
      case _ => None
    }
  }

  private def simpleParameterEditor(simpleEditor: SimpleEditor) = {
    simpleEditor.`type`() match {
      case SimpleEditorType.BOOL_EDITOR => BoolParameterEditor
      case SimpleEditorType.STRING_EDITOR => StringParameterEditor
      case SimpleEditorType.FIXED_VALUES_EDITOR => FixedValuesParameterEditor(
        simpleEditor.possibleValues()
          .map(value => FixedExpressionValue(value.expression(), value.label()))
          .toList
      )
      case SimpleEditorType.DATE_EDITOR => DateParameterEditor
      case SimpleEditorType.TIME_EDITOR => TimeParameterEditor
      case SimpleEditorType.DATE_TIME_EDITOR => DateTimeParameterEditor
      case SimpleEditorType.DURATION_EDITOR => DurationParameterEditor(simpleEditor.timeRangeComponents())
      case SimpleEditorType.PERIOD_EDITOR => PeriodParameterEditor(simpleEditor.timeRangeComponents())
      case SimpleEditorType.CRON_EDITOR => CronParameterEditor
    }
  }
}
