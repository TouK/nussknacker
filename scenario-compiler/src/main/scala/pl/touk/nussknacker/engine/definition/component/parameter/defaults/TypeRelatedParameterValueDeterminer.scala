package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import pl.touk.nussknacker.engine.api.definition.ParameterEditor
import pl.touk.nussknacker.engine.api.typed.StandardTypesClasses
import pl.touk.nussknacker.engine.api.typed.StandardTypesClasses._
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, TypingResult}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion

protected object TypeRelatedParameterValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[Expression] = {
    determineTypeRelatedDefaultParamValue(parameters.determinedEditors.head, parameters.parameterData.typing)
  }

  private[defaults] def determineTypeRelatedDefaultParamValue(
      firstEditor: ParameterEditor,
      typ: TypingResult
  ): Option[Expression] = {
    val firstEditorLanguage = EditorBasedLanguageDeterminer.determineLanguageOf(firstEditor)
    val klass = typ match {
      case s: SingleTypingResult =>
        Some(s.runtimeObjType.klass)
      case _ =>
        None
    }
    Option((firstEditorLanguage, klass)).collect {
      case (Language.Spel, Some(clazz)) if StandardTypesClasses.isDecimalNumber(clazz)       => "0".spel
      case (Language.Spel, Some(clazz)) if StandardTypesClasses.isFloatingPointNumber(clazz) => "0.0".spel
      case (Language.Spel, Some(`BooleanClass`))                                             => "true".spel
      case (Language.Spel, Some(`StringClass`))                                              => "''".spel
      case (Language.Spel, Some(`ListClass` | `ArrayClass`))                                 => "{}".spel
      case (Language.Spel, Some(`MapClass`))                                                 => "{:}".spel
      case (Language.Spel, _)                                                                => "".spel
      case (Language.Json | Language.JsonTemplate, _) =>
        Expression(
          firstEditorLanguage,
          InitialJsonFromTypingResultDeterminer
            .initialJson(typ)
            .spaces2
        )
    }
  }

}
