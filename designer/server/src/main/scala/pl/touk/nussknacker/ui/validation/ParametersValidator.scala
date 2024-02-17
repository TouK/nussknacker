package pl.touk.nussknacker.ui.validation

import cats.data.Validated.Invalid
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.spel.SpelExpressionParser
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeValidationError
import pl.touk.nussknacker.ui.api.ParametersValidationRequest

class ParametersValidator(modelData: ModelData, scenarioPropertiesNames: Iterable[String]) {

  private val expressionCompiler = ExpressionCompiler.withoutOptimization(modelData).withLabelsDictTyper

  private val validationContextGlobalVariablesOnly =
    GlobalVariablesPreparer(modelData.modelDefinition.expressionConfig)
      .prepareValidationContextWithGlobalVariablesOnly(scenarioPropertiesNames)

  def validate(
      request: ParametersValidationRequest,
  ): List[NodeValidationError] = {
    val context = validationContextGlobalVariablesOnly.copy(localVariables = request.variableTypes)
    request.parameters
      .map(param => expressionCompiler.compile(param.expression, Some(param.name), context, param.typ)(NodeId("")))
      .collect { case Invalid(a) => a.map(PrettyValidationErrors.formatErrorMessage).toList }
      .flatten
  }

}
