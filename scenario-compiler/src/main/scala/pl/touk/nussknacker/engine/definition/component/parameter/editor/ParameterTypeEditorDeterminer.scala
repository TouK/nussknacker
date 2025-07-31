package pl.touk.nussknacker.engine.definition.component.parameter.editor

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.ModelConfig.GlobalParametersConfig
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.typed.StandardTypesClasses._
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, TypingResult}

import java.time.temporal.ChronoUnit

class ParameterTypeEditorDeterminer(val typ: TypingResult, globalParametersConfig: GlobalParametersConfig) {

  def determine(): NonEmptyList[ParameterEditor] = {
    Option(typ)
      .collect { case s: SingleTypingResult =>
        s.runtimeObjType
      }
      .map(_.klass)
      .collect {
        case klazz if klazz.isEnum =>
          withSpelEditorAsFallback(
            FixedValuesParameterEditor(
              possibleValues = klazz.getEnumConstants.toList.map(ParameterTypeEditorDeterminer.extractEnumValue(klazz))
            )
          )
        case klazz if classOf[java.lang.CharSequence].isAssignableFrom(klazz) =>
          globalParametersConfig.editorsForStringType
        case `LocalDateTimeClass` =>
          withSpelEditorAsFallback(
            DateTimeParameterEditor
          )
        case `LocalTimeClass` =>
          withSpelEditorAsFallback(
            TimeParameterEditor
          )
        case `LocalDateClass` =>
          withSpelEditorAsFallback(
            DateParameterEditor
          )
        case `DurationClass` =>
          withSpelEditorAsFallback(
            DurationParameterEditor(List(ChronoUnit.DAYS, ChronoUnit.HOURS, ChronoUnit.MINUTES))
          )
        case `PeriodClass` =>
          withSpelEditorAsFallback(
            PeriodParameterEditor(List(ChronoUnit.YEARS, ChronoUnit.MONTHS, ChronoUnit.DAYS))
          )
        // we use class name to avoid introducing dependency on cronutils in interpreter
        case klazz if klazz.getName == "com.cronutils.model.Cron" =>
          withSpelEditorAsFallback(
            CronParameterEditor
          )
      }
      .getOrElse(NonEmptyList.one(SpelParameterEditor))
  }

  private def withSpelEditorAsFallback(editor: ParameterEditor) =
    NonEmptyList.of(editor, SpelParameterEditor)

}

object ParameterTypeEditorDeterminer {

  // mainly for tests
  def extractEnumValue(enumClass: Class[_])(enumConst: Any): FixedExpressionValue = {
    val enumConstName = enumClass.getMethod("name").invoke(enumConst)
    FixedExpressionValue(s"T(${enumClass.getName}).$enumConstName", enumConst.toString)
  }

}
