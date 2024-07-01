package pl.touk.nussknacker.ui.suggester

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.dict.UiDictServices
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.spel.{ExpressionSuggestion, SpelExpressionSuggester}
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet

import scala.concurrent.{ExecutionContext, Future}

class ExpressionSuggester(
    expressionDefinition: ExpressionConfigDefinition,
    classDefinitions: ClassDefinitionSet,
    uiDictServices: UiDictServices,
    classLoader: ClassLoader,
    scenarioPropertiesNames: Iterable[String]
) {

  private val spelExpressionSuggester =
    new SpelExpressionSuggester(expressionDefinition, classDefinitions, uiDictServices, classLoader)

  private val validationContextGlobalVariablesOnly =
    GlobalVariablesPreparer(expressionDefinition).prepareValidationContextWithGlobalVariablesOnly(
      scenarioPropertiesNames
    )

  private def restrictExpressionByCaretPosition2d(
      expression: Expression,
      caretPosition2d: CaretPosition2d
  ): Expression = {
    val transformedPlainExpression = expression.expression
      .split("\n")
      .toList
      .zipWithIndex
      .filter(_._2 <= caretPosition2d.row)
      .map {
        case (s, caretPosition2d.row) => (s.take(caretPosition2d.column), caretPosition2d.row)
        case (s, i)                   => (s, i)
      }
      .map(_._1)
      .mkString("\n")

    expression.copy(expression = transformedPlainExpression)
  }

  def expressionSuggestions(
      expression: Expression,
      caretPosition2d: CaretPosition2d,
      localVariables: Map[String, TypingResult]
  )(implicit ec: ExecutionContext): Future[List[ExpressionSuggestion]] = {
    val validationContext = validationContextGlobalVariablesOnly.copy(localVariables = localVariables)
    expression.language match {
      // currently we only support Spel and SpelTemplate expressions
      case Language.Spel | Language.SpelTemplate =>
        spelExpressionSuggester
          .expressionSuggestions(
            expression,
            caretPosition2d.normalizedCaretPosition(expression.expression),
            validationContext
          )
          .flatMap {
            case Nil =>
              val newExpression: Expression = restrictExpressionByCaretPosition2d(expression, caretPosition2d)

              spelExpressionSuggester.expressionSuggestions(
                newExpression,
                caretPosition2d.normalizedCaretPosition(newExpression.expression),
                validationContext
              )
            case suggestions => Future.successful(suggestions)
          }
      case _ => Future.successful(Nil)
    }
  }

}

object ExpressionSuggester {

  def apply(modelData: ModelData, scenarioPropertiesNames: Iterable[String]): ExpressionSuggester = {
    new ExpressionSuggester(
      modelData.modelDefinition.expressionConfig,
      modelData.modelDefinitionWithClasses.classDefinitions,
      modelData.designerDictServices,
      modelData.modelClassLoader.classLoader,
      scenarioPropertiesNames
    )
  }

}

@JsonCodec
final case class CaretPosition2d(row: Int, column: Int) {

  def normalizedCaretPosition(inputValue: String): Int = {
    inputValue.split("\n").take(row).map(_.length).sum + row + column
  }

}
