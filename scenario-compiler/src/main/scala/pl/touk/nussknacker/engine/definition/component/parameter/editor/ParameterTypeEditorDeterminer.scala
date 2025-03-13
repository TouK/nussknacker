package pl.touk.nussknacker.engine.definition.component.parameter.editor

import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, TypingResult}

import java.time.temporal.ChronoUnit

class ParameterTypeEditorDeterminer(val typ: TypingResult) extends ParameterEditorDeterminer {

  override def determine(): Option[ParameterEditors] = {
    Option(typ)
      .collect { case s: SingleTypingResult =>
        s.runtimeObjType
      }
      .map(_.klass)
      .collect {
        case klazz if klazz.isEnum =>
          ParameterEditors(
            FixedValuesParameterEditor(
              possibleValues = klazz.getEnumConstants.toList.map(ParameterTypeEditorDeterminer.extractEnumValue(klazz))
            ),
            SpelParameterEditor
          )
        case klazz if classOf[java.lang.CharSequence].isAssignableFrom(klazz) =>
          ParameterEditors(
            SpelParameterEditor,
            SpelTemplateParameterEditor,
          )
        case klazz if klazz == classOf[java.time.LocalDateTime] =>
          ParameterEditors(
            DateTimeParameterEditor,
            SpelParameterEditor,
          )
        case klazz if klazz == classOf[java.time.LocalTime] =>
          ParameterEditors(
            TimeParameterEditor,
            SpelParameterEditor
          )
        case klazz if klazz == classOf[java.time.LocalDate] =>
          ParameterEditors(
            DateParameterEditor,
            SpelParameterEditor
          )
        case klazz if klazz == classOf[java.time.Duration] =>
          ParameterEditors(
            DurationParameterEditor(List(ChronoUnit.DAYS, ChronoUnit.HOURS, ChronoUnit.MINUTES)),
            SpelParameterEditor
          )
        case klazz if klazz == classOf[java.time.Period] =>
          ParameterEditors(
            PeriodParameterEditor(List(ChronoUnit.YEARS, ChronoUnit.MONTHS, ChronoUnit.DAYS)),
            SpelParameterEditor
          )
        // we use class name to avoid introducing dependency on cronutils in interpreter
        case klazz if klazz.getName == "com.cronutils.model.Cron" =>
          ParameterEditors(
            CronParameterEditor,
            SpelParameterEditor
          )
      }
  }

}

object ParameterTypeEditorDeterminer {

  // mainly for tests
  def extractEnumValue(enumClass: Class[_])(enumConst: Any): FixedExpressionValue = {
    val enumConstName = enumClass.getMethod("name").invoke(enumConst)
    FixedExpressionValue(s"T(${enumClass.getName}).$enumConstName", enumConst.toString)
  }

}
