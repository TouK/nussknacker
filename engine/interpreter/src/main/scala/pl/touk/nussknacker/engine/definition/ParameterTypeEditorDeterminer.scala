package pl.touk.nussknacker.engine.definition

import java.time.temporal.ChronoUnit

import pl.touk.nussknacker.engine.api.definition.{DateParameterEditor, DateTimeParameterEditor, DualParameterEditor, DurationParameterEditor, FixedExpressionValue, FixedValuesParameterEditor, ParameterEditor, PeriodParameterEditor, StringParameterEditor, TimeParameterEditor}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode

class ParameterTypeEditorDeterminer(val runtimeClass: Class[_]) extends ParameterEditorDeterminer {

  override def determine(): Option[ParameterEditor] = {
    runtimeClass match {
      case klazz if klazz.isEnum => Some(
        FixedValuesParameterEditor(
          possibleValues = runtimeClass.getEnumConstants.toList.map(extractEnumValue(runtimeClass))
        )
      )
      case klazz if klazz == classOf[java.lang.String] => Some(
        DualParameterEditor(
          simpleEditor = StringParameterEditor,
          defaultMode = DualEditorMode.RAW
        )
      )
      case klazz if klazz == classOf[java.time.LocalDateTime] => Some(
        DualParameterEditor(
          simpleEditor = DateTimeParameterEditor,
          defaultMode = DualEditorMode.SIMPLE
        )
      )
      case klazz if klazz == classOf[java.time.LocalTime] => Some(
        DualParameterEditor(
          simpleEditor = TimeParameterEditor,
          defaultMode = DualEditorMode.SIMPLE
        )
      )
      case klazz if klazz == classOf[java.time.LocalDate] => Some(
        DualParameterEditor(
          simpleEditor = DateParameterEditor,
          defaultMode = DualEditorMode.SIMPLE
        )
      )
      case klazz if klazz == classOf[java.time.Duration] => Some(
        DualParameterEditor(
          simpleEditor = DurationParameterEditor(List(ChronoUnit.DAYS, ChronoUnit.HOURS, ChronoUnit.MINUTES)),
          defaultMode = DualEditorMode.SIMPLE
        )
      )
      case klazz if klazz == classOf[java.time.Period] => Some(
        DualParameterEditor(
          simpleEditor = PeriodParameterEditor(List(ChronoUnit.YEARS, ChronoUnit.MONTHS, ChronoUnit.DAYS)),
          defaultMode = DualEditorMode.SIMPLE
        )
      )
      case _ => None
    }
  }

  private def extractEnumValue(enumClass: Class[_])(enumConst: Any): FixedExpressionValue = {
    val enumConstName = enumClass.getMethod("name").invoke(enumConst)
    FixedExpressionValue(s"T(${enumClass.getName}).$enumConstName", enumConst.toString)
  }
}
