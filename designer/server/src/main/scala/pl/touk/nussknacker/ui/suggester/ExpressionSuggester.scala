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
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet
import pl.touk.nussknacker.engine.util.CaretPosition2d

import scala.concurrent.{ExecutionContext, Future}

// todo: lbg imports
class ExpressionSuggester(
    expressionDefinition: ExpressionConfigDefinition,
    classDefinitions: ClassDefinitionSet,
    uiDictServices: UiDictServices,
    classLoader: ClassLoader,
    scenarioPropertiesNames: Iterable[String],
    settings: ClassExtractionSettings,
) {

  private val spelExpressionSuggester =
    new SpelExpressionSuggester(expressionDefinition, classDefinitions, uiDictServices, classLoader, settings)

  private val validationContextGlobalVariablesOnly =
    GlobalVariablesPreparer(expressionDefinition).prepareValidationContextWithGlobalVariablesOnly(
      scenarioPropertiesNames
    )

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
            caretPosition2d,
            validationContext
          )
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
      scenarioPropertiesNames,
      modelData.modelDefinition.settings
    )
  }

}
