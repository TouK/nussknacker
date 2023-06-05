package pl.touk.nussknacker.ui.api

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.TypeDefinitionSet
import pl.touk.nussknacker.engine.api.dict.DictQueryService
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ExpressionDefinition
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.spel.{ExpressionSuggestion, SpelExpressionSuggester}

import scala.concurrent.{ExecutionContext, Future}

class ExpressionSuggester(val expressionConfig: ExpressionDefinition[_], val typeDefinitions: TypeDefinitionSet, val dictQueryService: DictQueryService, val classLoader: ClassLoader) {

  private val spelExpressionSuggester = new SpelExpressionSuggester(expressionConfig, typeDefinitions, dictQueryService, classLoader)

  def expressionSuggestions(expression: Expression, caretPosition2d: CaretPosition2d, variables: Map[String, TypingResult])
                           (implicit ec: ExecutionContext): Future[List[ExpressionSuggestion]] = {
    expression.language match {
      // currently we only support Spel expressions
      case Language.Spel => spelExpressionSuggester.expressionSuggestions(expression, caretPosition2d.normalizedCaretPosition(expression.expression), variables)
      case _ => Future.successful(Nil)
    }
  }
}

@JsonCodec(decodeOnly = true)
case class CaretPosition2d(row: Int, column: Int) {
  def normalizedCaretPosition(inputValue: String): Int = {
    inputValue.split("\n").take(row).map(_.length).sum + row + column
  }
}
