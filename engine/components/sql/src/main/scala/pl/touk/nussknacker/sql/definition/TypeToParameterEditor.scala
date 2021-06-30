package pl.touk.nussknacker.sql.definition

import pl.touk.nussknacker.engine.api.definition.{CronParameterEditor, DateParameterEditor, DateTimeParameterEditor, DualParameterEditor, DurationParameterEditor, FixedExpressionValue, FixedValuesParameterEditor, ParameterEditor, PeriodParameterEditor, StringParameterEditor, TimeParameterEditor}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, TypingResult}

import java.time.temporal.ChronoUnit

// Copied from pl.touk.nussknacker.engine.definition.parameter.editor.ParameterTypeEditorDeterminer
object TypeToParameterEditor {

  def apply(typ: TypingResult): Option[ParameterEditor] = {
    Option(typ).collect {
      case s: SingleTypingResult => s.objType
    }.map(_.klass).collect {
      case klazz if klazz.isEnum =>
        // We can pick here simple editor, but for compatibility reasons we choose dual editor instead
        // - to be able to provide some other expression
        DualParameterEditor(FixedValuesParameterEditor(
          possibleValues = klazz.getEnumConstants.toList.map(extractEnumValue(klazz))
        ), DualEditorMode.SIMPLE)
      case klazz if klazz == classOf[java.lang.String] =>
        DualParameterEditor(
          simpleEditor = StringParameterEditor,
          defaultMode = DualEditorMode.RAW
        )
      case klazz if klazz == classOf[java.time.LocalDateTime] =>
        DualParameterEditor(
          simpleEditor = DateTimeParameterEditor,
          defaultMode = DualEditorMode.SIMPLE
        )
      case klazz if klazz == classOf[java.time.LocalTime] =>
        DualParameterEditor(
          simpleEditor = TimeParameterEditor,
          defaultMode = DualEditorMode.SIMPLE
        )
      case klazz if klazz == classOf[java.time.LocalDate] =>
        DualParameterEditor(
          simpleEditor = DateParameterEditor,
          defaultMode = DualEditorMode.SIMPLE
        )
      case klazz if klazz == classOf[java.time.Duration] =>
        DualParameterEditor(
          simpleEditor = DurationParameterEditor(List(ChronoUnit.DAYS, ChronoUnit.HOURS, ChronoUnit.MINUTES)),
          defaultMode = DualEditorMode.SIMPLE
        )
      case klazz if klazz == classOf[java.time.Period] =>
        DualParameterEditor(
          simpleEditor = PeriodParameterEditor(List(ChronoUnit.YEARS, ChronoUnit.MONTHS, ChronoUnit.DAYS)),
          defaultMode = DualEditorMode.SIMPLE
        )
      //we use class name to avoid introducing dependency on cronutils in interpreter
      case klazz if klazz.getName == "com.cronutils.model.Cron" => DualParameterEditor(
        simpleEditor = CronParameterEditor,
        defaultMode = DualEditorMode.SIMPLE
      )
    }
  }

  private def extractEnumValue(enumClass: Class[_])(enumConst: Any): FixedExpressionValue = {
    val enumConstName = enumClass.getMethod("name").invoke(enumConst)
    FixedExpressionValue(s"T(${enumClass.getName}).$enumConstName", enumConst.toString)
  }
}
