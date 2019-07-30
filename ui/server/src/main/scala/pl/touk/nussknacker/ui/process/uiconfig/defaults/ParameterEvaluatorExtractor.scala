package pl.touk.nussknacker.ui.process.uiconfig.defaults

import pl.touk.nussknacker.engine.api.definition
import pl.touk.nussknacker.engine.api.process.{ParameterConfig, SingleNodeConfig}
import pl.touk.nussknacker.engine.definition.defaults.{NodeDefinition, ParameterDefaultValueExtractorStrategy}
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression

class ParameterEvaluatorExtractor(defaultValueEvaluator: ParameterDefaultValueExtractorStrategy) {
  def evaluateParameters(nodeDefinition: NodeDefinition, config: SingleNodeConfig): List[Parameter] = {
    val strategy: definition.Parameter => Option[String] = p => defaultValueEvaluator.evaluateParameterDefaultValue(nodeDefinition, p)
    nodeDefinition.parameters
      .filterNot(_.branchParam)
      .map(mapDefinitionParamToEvaluatedParam(strategy, config))
  }

  def evaluateBranchParameters(nodeDefinition: NodeDefinition, config: SingleNodeConfig): List[Parameter] = {
    val strategy: definition.Parameter => Option[String] = p => defaultValueEvaluator.evaluateParameterDefaultValue(nodeDefinition, p)
    nodeDefinition.parameters
      .filter(_.branchParam)
      .map(mapDefinitionParamToEvaluatedParam(strategy, config))
  }

  private def ensureParameterDefaultValue(strategy: definition.Parameter => Option[String])(param: definition.Parameter): String =
    strategy(param) match {
      case Some(v) => v
      case None => throw new IllegalStateException("Eventually parameter have to have some value")
    }

  private def mapDefinitionParamToEvaluatedParam(strategy: definition.Parameter => Option[String], config: SingleNodeConfig)(param: definition.Parameter): Parameter = {
    val paramConfig = config.params.getOrElse(Map()).getOrElse(param.name, ParameterConfig.empty)
    createSpelExpressionParameter(param, paramConfig, ensureParameterDefaultValue(strategy))
  }

  private def createSpelExpressionParameter(parameter: definition.Parameter,
                                            config: ParameterConfig,
                                            valueEvaluatorStrategy: definition.Parameter => String): Parameter =
    Parameter(parameter.name, Expression(config.language.getOrElse("spel"), valueEvaluatorStrategy(parameter)))
}