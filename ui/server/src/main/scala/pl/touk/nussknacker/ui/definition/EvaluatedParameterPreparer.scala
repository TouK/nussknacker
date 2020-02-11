package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.engine.api.defaults.{NodeDefinition, ParameterDefaultValueDeterminer}
import pl.touk.nussknacker.engine.api.definition
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression

class EvaluatedParameterPreparer(defaultValueEvaluator: ParameterDefaultValueDeterminer) {
  def prepareEvaluatedParameter(nodeDefinition: NodeDefinition): List[Parameter] = {
    val strategy: definition.Parameter => Option[String] = p => defaultValueEvaluator.determineParameterDefaultValue(nodeDefinition, p)
    nodeDefinition.parameters
      .filterNot(_.branchParam)
      .map(mapDefinitionParamToEvaluatedParam(strategy))
  }

  def prepareEvaluatedBranchParameter(nodeDefinition: NodeDefinition): List[Parameter] = {
    val strategy: definition.Parameter => Option[String] = p => defaultValueEvaluator.determineParameterDefaultValue(nodeDefinition, p)
    nodeDefinition.parameters
      .filter(_.branchParam)
      .map(mapDefinitionParamToEvaluatedParam(strategy))
  }

  private def determineDefaultValue(strategy: definition.Parameter => Option[String])(param: definition.Parameter): String =
    strategy(param) match {
      case Some(v) => v
      case None => "" // parameter have to have some default value
    }

  private def mapDefinitionParamToEvaluatedParam(strategy: definition.Parameter => Option[String])(param: definition.Parameter): Parameter = {
    //TODO: enable nicer handling of constants/simple strings (maybe SpEL templates??)
    createSpelExpressionParameter(param, determineDefaultValue(strategy))
  }

  private def createSpelExpressionParameter(parameter: definition.Parameter,
                                            valueEvaluatorStrategy: definition.Parameter => String): Parameter =
    Parameter(parameter.name, Expression("spel", valueEvaluatorStrategy(parameter)))
}
