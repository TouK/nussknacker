package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.engine.api.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.api.graph.expression.Expression
import pl.touk.nussknacker.restmodel.definition.UIParameter

object EvaluatedParameterPreparer {
  def prepareEvaluatedParameter(parameters: List[UIParameter]): List[Parameter] = {
    parameters
      .filterNot(_.branchParam)
      .map(createSpelExpressionParameter)
  }

  def prepareEvaluatedBranchParameter(parameters: List[UIParameter]): List[Parameter] = {
    parameters
      .filter(_.branchParam)
      .map(createSpelExpressionParameter)
  }

  private def createSpelExpressionParameter(parameter: UIParameter): Parameter =
    Parameter(parameter.name, Expression("spel", parameter.defaultValue.getOrElse("")))
}
