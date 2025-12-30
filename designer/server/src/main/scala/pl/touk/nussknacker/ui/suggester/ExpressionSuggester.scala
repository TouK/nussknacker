package pl.touk.nussknacker.ui.suggester

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.spel.{ExpressionSuggestion, SpelExpressionSuggester}
import pl.touk.nussknacker.engine.util.CaretPosition2d
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.ui.process.test.testcase.TestCaseVariables

import scala.concurrent.{ExecutionContext, Future}

class ExpressionSuggester(
    spelExpressionSuggester: SpelExpressionSuggester,
    validationContextGlobalVariablesOnly: ValidationContext,
) {

  def expressionSuggestions(
      expression: Expression,
      caretPosition2d: CaretPosition2d,
      localVariables: Map[String, TypingResult]
  )(implicit ec: ExecutionContext): Future[List[ExpressionSuggestion]] = {
    val validationContext = validationContextGlobalVariablesOnly.copy(localVariables = localVariables)
    expression.language match {
      // currently we only support Spel, SpelTemplate and JsonTemplate expressions
      case Language.Spel | Language.SpelTemplate | Language.JsonTemplate =>
        spelExpressionSuggester
          .expressionSuggestions(
            expression,
            caretPosition2d,
            validationContext
          )
      case _ => Future.successful(Nil)
    }
  }

}

object ExpressionSuggester {

  def apply(modelData: ModelData, scenarioPropertiesNames: Iterable[String]): ExpressionSuggester = {
    val classDefinitionsForSuggester = TestCaseVariables.extendClassDefinitionSet(
      modelData.modelDefinitionWithClasses.classDefinitions,
      modelData.modelDefinition.settings
    )
    val spelExpressionSuggester =
      new SpelExpressionSuggester(
        modelData.modelDefinition.expressionConfig,
        classDefinitionsForSuggester,
        modelData.designerDictServices,
        modelData.modelClassLoader
      )
    val validationContextGlobalVariablesOnly =
      TestCaseVariables.extendGlobalVariablesValidationContext(
        GlobalVariablesPreparer(modelData.modelDefinition.expressionConfig)
          .prepareValidationContextWithGlobalVariablesOnly(scenarioPropertiesNames)
      )
    new ExpressionSuggester(
      spelExpressionSuggester,
      validationContextGlobalVariablesOnly,
    )
  }

}
