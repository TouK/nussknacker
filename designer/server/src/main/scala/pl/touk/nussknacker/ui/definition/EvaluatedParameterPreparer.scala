package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => EvaluatedParameter}
import pl.touk.nussknacker.engine.graph.expression.Expression

object EvaluatedParameterPreparer {
  def prepareEvaluatedParameter(parameters: List[Parameter]): List[EvaluatedParameter] = {
    parameters
      .filterNot(_.branchParam)
      .map(createSpelExpressionParameter)
  }

  def prepareEvaluatedBranchParameter(parameters: List[Parameter]): List[EvaluatedParameter] = {
    parameters
      .filter(_.branchParam)
      .map(createSpelExpressionParameter)
  }

  private def createSpelExpressionParameter(parameter: Parameter): EvaluatedParameter =
    EvaluatedParameter(parameter.name, parameter.defaultValue.getOrElse(Expression.spel("")))
}
