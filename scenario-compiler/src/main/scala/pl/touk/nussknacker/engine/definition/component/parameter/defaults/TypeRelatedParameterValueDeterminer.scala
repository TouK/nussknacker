package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import pl.touk.nussknacker.engine.api.definition.ParameterEditor
import pl.touk.nussknacker.engine.api.typed.typing.SingleTypingResult
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language

protected object TypeRelatedParameterValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[Expression] = {
    val klass = parameters.parameterData.typing match {
      case s: SingleTypingResult =>
        Some(s.objType.klass)
      case _ =>
        None
    }
    klass.flatMap(determineTypeRelatedDefaultParamValue(parameters.determinedEditor, _))
  }

  private[defaults] def determineTypeRelatedDefaultParamValue(
      editor: Option[ParameterEditor],
      className: Class[_]
  ): Option[Expression] = {
    // TODO: use classes instead of class names
    Option(className).map(_.getName).collect {
      case "long" | "short" | "int" | "java.lang.Number" | "java.lang.Long" |
          "java.lang.Short" | "java.lang.Integer" | "java.math.BigInteger" =>
        Expression.spel("0")
      case "float" | "double" | "java.math.BigDecimal" | "java.lang.Float" | "java.lang.Double" =>
        Expression.spel("0.0")
      case "boolean" | "java.lang.Boolean" => Expression.spel("true")
      case "java.lang.String"              => defaultStringExpression(editor)
      case "java.util.List"                => Expression.spel("{}")
      case "java.util.Map"                 => Expression.spel("{:}")
    }
  }

  private def defaultStringExpression(editor: Option[ParameterEditor]): Expression =
    EditorBasedLanguageDeterminer.determineLanguageOf(editor) match {
      case Language.Spel => Expression.spel("''")
      case language @ (Language.SpelTemplate | Language.DictKeyWithLabel | Language.TabularDataDefinition) =>
        Expression(language, "")
    }

}
