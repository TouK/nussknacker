package pl.touk.nussknacker.ui.process.uiconfig.defaults

import pl.touk.nussknacker.engine.api.definition
import pl.touk.nussknacker.engine.definition.defaults.{NodeDefinition, ParameterDefaultValueExtractorStrategy}
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression

class ParameterEvaluatorExtractor(defaultValueEvaluator: ParameterDefaultValueExtractorStrategy) {
  def evaluateParameters(nodeDefinition: NodeDefinition): List[Parameter] = {
    val strategy: definition.Parameter => Option[String] = p => defaultValueEvaluator.evaluateParameterDefaultValue(nodeDefinition, p)
    nodeDefinition.parameters
      .filterNot(_.branchParam)
      .map(mapDefinitionParamToEvaluatedParam(strategy))
  }

  def evaluateBranchParameters(nodeDefinition: NodeDefinition): List[Parameter] = {
    val strategy: definition.Parameter => Option[String] = p => defaultValueEvaluator.evaluateParameterDefaultValue(nodeDefinition, p)
    nodeDefinition.parameters
      .filter(_.branchParam)
      .map(mapDefinitionParamToEvaluatedParam(strategy))
  }

  private def ensureParameterDefaultValue(strategy: definition.Parameter => Option[String])(param: definition.Parameter): String =
    strategy(param) match {
      case Some(v) => v
      case None => throw new IllegalStateException("Eventually parameter have to have some value")
    }

  private def mapDefinitionParamToEvaluatedParam(strategy: definition.Parameter => Option[String])(param: definition.Parameter): Parameter = {
    //TODO: enable nicer handling of constants/simple strings (maybe SpEL templates??)
    createSpelExpressionParameter(param, ensureParameterDefaultValue(strategy))
  }

  private def createSpelExpressionParameter(parameter: definition.Parameter,
                                            valueEvaluatorStrategy: definition.Parameter => String): Parameter =
    Parameter(parameter.name, Expression("spel", valueEvaluatorStrategy(parameter)))
}