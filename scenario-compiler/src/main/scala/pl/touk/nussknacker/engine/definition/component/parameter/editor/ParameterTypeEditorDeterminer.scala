package pl.touk.nussknacker.engine.definition.component.parameter.editor

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.ModelConfig.GlobalParametersConfig
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.typed.StandardTypesClasses._
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, TypingResult}

import java.time.temporal.ChronoUnit

class ParameterTypeEditorDeterminer(
    typ: TypingResult,
    isLazyParameter: Boolean,
    globalParametersConfig: GlobalParametersConfig
) {

  def determine(): NonEmptyList[ParameterEditor] = {
    Option(typ)
      .collect { case s: SingleTypingResult =>
        s.runtimeObjType
      }
      .map(_.klass)
      .collect {
        case klazz if klazz.isEnum =>
          withSpelEditorAsFallbackForLazyParameter(
            FixedValuesParameterEditor(
              possibleValues = klazz.getEnumConstants.toList.map(ParameterTypeEditorDeterminer.extractEnumValue(klazz))
            )
          )
        case klazz if classOf[java.lang.CharSequence].isAssignableFrom(klazz) =>
          globalParametersConfig.editorsForStringType
        case `BooleanClass` =>
          withSpelEditorAsFallbackForLazyParameter(
            BoolParameterEditor
          )
        case `LocalDateTimeClass` =>
          withSpelEditorAsFallbackForLazyParameter(
            DateTimeParameterEditor
          )
        case `LocalTimeClass` =>
          withSpelEditorAsFallbackForLazyParameter(
            TimeParameterEditor
          )
        case `LocalDateClass` =>
          withSpelEditorAsFallbackForLazyParameter(
            DateParameterEditor
          )
        case `DurationClass` =>
          withSpelEditorAsFallbackForLazyParameter(
            DurationParameterEditor(List(ChronoUnit.DAYS, ChronoUnit.HOURS, ChronoUnit.MINUTES))
          )
        case `PeriodClass` =>
          withSpelEditorAsFallbackForLazyParameter(
            PeriodParameterEditor(List(ChronoUnit.YEARS, ChronoUnit.MONTHS, ChronoUnit.DAYS))
          )
        // we use class name to avoid introducing dependency on cronutils in interpreter
        case klazz if klazz.getName == "com.cronutils.model.Cron" =>
          withSpelEditorAsFallbackForLazyParameter(
            CronParameterEditor
          )
      }
      .getOrElse(NonEmptyList.one(SpelParameterEditor))
  }

  private def withSpelEditorAsFallbackForLazyParameter(editor: ParameterEditor) =
    if (isLazyParameter) {
      NonEmptyList.of(editor, SpelParameterEditor)
    } else {
      NonEmptyList.one(editor)
    }

}

object ParameterTypeEditorDeterminer {

  // mainly for tests
  def extractEnumValue(enumClass: Class[_])(enumConst: Any): FixedExpressionValue = {
    val enumConstName = enumClass.getMethod("name").invoke(enumConst)
    FixedExpressionValue(s"T(${enumClass.getName}).$enumConstName", enumConst.toString)
  }

}
