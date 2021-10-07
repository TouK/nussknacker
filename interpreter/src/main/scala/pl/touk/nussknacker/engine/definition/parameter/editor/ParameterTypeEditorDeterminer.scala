package pl.touk.nussknacker.engine.definition.parameter.editor

import java.time.temporal.ChronoUnit

import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, TypingResult}

class ParameterTypeEditorDeterminer(val typ: TypingResult) extends ParameterEditorDeterminer {

  override def determine(): Option[ParameterEditor] = {
    Option(typ).collect {
      case s: SingleTypingResult => s.objType
    }.map(_.klass).collect {
      case klazz if klazz.isEnum =>
        // We can pick here simple editor, but for compatibility reasons we choose dual editor instead
        // - to be able to provide some other expression
        DualParameterEditor(FixedValuesParameterEditor(
          possibleValues = klazz.getEnumConstants.toList.map(ParameterTypeEditorDeterminer.extractEnumValue(klazz))
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

}

object ParameterTypeEditorDeterminer {

  //mainly for tests
  def extractEnumValue(enumClass: Class[_])(enumConst: Any): FixedExpressionValue = {
    val enumConstName = enumClass.getMethod("name").invoke(enumConst)
    FixedExpressionValue(s"T(${enumClass.getName}).$enumConstName", enumConst.toString)
  }

}


