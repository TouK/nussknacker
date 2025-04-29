package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import pl.touk.nussknacker.engine.api.definition.ParameterEditor
import pl.touk.nussknacker.engine.api.typed.typing.SingleTypingResult
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language

protected object TypeRelatedParameterValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[Expression] = {
    val klass = parameters.parameterData.typing match {
      case s: SingleTypingResult =>
        Some(s.runtimeObjType.klass)
      case _ =>
        None
    }
    klass.flatMap(determineTypeRelatedDefaultParamValue(parameters.determinedEditors.headOption, _))
  }

  private[defaults] def determineTypeRelatedDefaultParamValue(
      editor: Option[ParameterEditor],
      className: Class[_]
  ): Option[Expression] = {
    // TODO: use classes instead of class names
    Option(className).collect {
      case className if TypeValueDeterminer.isIntegerNumber(className)       => Expression.spel("0")
      case className if TypeValueDeterminer.isFloatingPointNumber(className) => Expression.spel("0.0")
      case className if TypeValueDeterminer.isBoolean(className)             => Expression.spel("true")
      case className if TypeValueDeterminer.isString(className)              => defaultStringExpression(editor)
      case className if TypeValueDeterminer.isList(className)                => Expression.spel("{}")
      case className if TypeValueDeterminer.isMap(className)                 => Expression.spel("{:}")
    }
  }

  private def defaultStringExpression(editor: Option[ParameterEditor]): Expression =
    EditorBasedLanguageDeterminer.determineLanguageOf(editor) match {
      case Language.Spel         => Expression.spel("''")
      case Language.Json         => Expression.json("{}")
      case Language.JsonTemplate => Expression.json("{}")
      case language @ (Language.SpelTemplate | Language.DictKeyWithLabel | Language.TabularDataDefinition) =>
        Expression(language, "")
    }

}
