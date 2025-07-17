package pl.touk.nussknacker.engine.definition.component.parameter.editor

import pl.touk.nussknacker.engine.ModelConfig.EditorConfig
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, TypingResult}

import java.time.temporal.ChronoUnit

class ParameterTypeEditorDeterminer(val typ: TypingResult, editorConfig: EditorConfig)
    extends ParameterEditorDeterminer {

  override def determine(): List[ParameterEditor] = {
    Option(typ)
      .collect { case s: SingleTypingResult =>
        s.runtimeObjType
      }
      .map(_.klass)
      .collect {
        case klazz if klazz.isEnum =>
          List(
            FixedValuesParameterEditor(
              possibleValues = klazz.getEnumConstants.toList.map(ParameterTypeEditorDeterminer.extractEnumValue(klazz))
            ),
            SpelParameterEditor
          )
        case klazz if classOf[java.lang.CharSequence].isAssignableFrom(klazz) =>
          editorConfig.editorsForStringType.toList
        case klazz if klazz == classOf[java.time.LocalDateTime] =>
          List(
            DateTimeParameterEditor,
            SpelParameterEditor,
          )
        case klazz if klazz == classOf[java.time.LocalTime] =>
          List(
            TimeParameterEditor,
            SpelParameterEditor
          )
        case klazz if klazz == classOf[java.time.LocalDate] =>
          List(
            DateParameterEditor,
            SpelParameterEditor
          )
        case klazz if klazz == classOf[java.time.Duration] =>
          List(
            DurationParameterEditor(List(ChronoUnit.DAYS, ChronoUnit.HOURS, ChronoUnit.MINUTES)),
            SpelParameterEditor
          )
        case klazz if klazz == classOf[java.time.Period] =>
          List(
            PeriodParameterEditor(List(ChronoUnit.YEARS, ChronoUnit.MONTHS, ChronoUnit.DAYS)),
            SpelParameterEditor
          )
        // we use class name to avoid introducing dependency on cronutils in interpreter
        case klazz if klazz.getName == "com.cronutils.model.Cron" =>
          List(
            CronParameterEditor,
            SpelParameterEditor
          )
      }
      .getOrElse(Nil)
  }

}

object ParameterTypeEditorDeterminer {

  // mainly for tests
  def extractEnumValue(enumClass: Class[_])(enumConst: Any): FixedExpressionValue = {
    val enumConstName = enumClass.getMethod("name").invoke(enumConst)
    FixedExpressionValue(s"T(${enumClass.getName}).$enumConstName", enumConst.toString)
  }

}
