package pl.touk.nussknacker.ui.api

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.TypeDefinitionSet
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.spel.{ExpressionSuggestion, SpelExpressionSuggester, SpelExpressionSuggesterWithTyper}

class ExpressionSuggester {

  private val spelExpressionSuggester = new SpelExpressionSuggesterWithTyper

  def expressionSuggestions(expression: Expression, caretPosition2d: CaretPosition2d, variables: Map[String, TypingResult], typeDefinitions: TypeDefinitionSet): List[ExpressionSuggestion] = {
    expression.language match {
      // currently we only support Spel expressions
      case Language.Spel => spelExpressionSuggester.expressionSuggestions(expression, caretPosition2d.normalizedCaretPosition(expression.expression), variables, typeDefinitions)
      case _ => Nil
    }
  }
}

@JsonCodec(decodeOnly = true)
case class CaretPosition2d(row: Int, column: Int) {
  def normalizedCaretPosition(inputValue: String): Int = {
    inputValue.split("\n").take(row).map(_.length).sum + row + column
  }
}
