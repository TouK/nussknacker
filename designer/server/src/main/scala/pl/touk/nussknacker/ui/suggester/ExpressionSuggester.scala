package pl.touk.nussknacker.ui.suggester

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.dict.UiDictServices
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ExpressionDefinition
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.spel.{ExpressionSuggestion, SpelExpressionSuggester}
import pl.touk.nussknacker.engine.{ModelData, TypeDefinitionSet}

import scala.concurrent.{ExecutionContext, Future}

class ExpressionSuggester(
    expressionConfig: ExpressionDefinition[_],
    typeDefinitions: TypeDefinitionSet,
    uiDictServices: UiDictServices,
    classLoader: ClassLoader
) {

  private val spelExpressionSuggester =
    new SpelExpressionSuggester(expressionConfig, typeDefinitions, uiDictServices, classLoader)

  def expressionSuggestions(
      expression: Expression,
      caretPosition2d: CaretPosition2d,
      variables: Map[String, TypingResult]
  )(implicit ec: ExecutionContext): Future[List[ExpressionSuggestion]] = {
    expression.language match {
      // currently we only support Spel and SpelTemplate expressions
      case Language.Spel | Language.SpelTemplate =>
        spelExpressionSuggester.expressionSuggestions(
          expression,
          caretPosition2d.normalizedCaretPosition(expression.expression),
          variables
        )
      case _ => Future.successful(Nil)
    }
  }

}

object ExpressionSuggester {

  def apply(modelData: ModelData): ExpressionSuggester = {
    new ExpressionSuggester(
      modelData.modelDefinition.expressionConfig,
      modelData.modelDefinitionWithTypes.typeDefinitions,
      modelData.uiDictServices,
      modelData.modelClassLoader.classLoader
    )
  }

}

@JsonCodec(decodeOnly = true)
final case class CaretPosition2d(row: Int, column: Int) {

  def normalizedCaretPosition(inputValue: String): Int = {
    inputValue.split("\n").take(row).map(_.length).sum + row + column
  }

}
