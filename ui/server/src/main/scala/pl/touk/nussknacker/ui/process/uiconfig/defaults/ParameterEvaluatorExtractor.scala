package pl.touk.nussknacker.ui.process.uiconfig.defaults

import pl.touk.nussknacker.engine.definition.DefinitionExtractor
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.ui.api.NodeDefinition

class ParameterEvaluatorExtractor(defaultValueEvaluator: ParameterDefaultValueExtractorStrategy) {
  def evaluateParameters(nodeDefinition: NodeDefinition): List[Parameter] = {
    val strategy: DefinitionExtractor.Parameter => Option[String] = p => defaultValueEvaluator.evaluateParameterDefaultValue(nodeDefinition, p)
    nodeDefinition.parameters
      .map(mapDefinitionParamToEvaluatedParam(strategy))
  }

  private def ensureParameterDefaultValue(strategy: DefinitionExtractor.Parameter => Option[String])(param: DefinitionExtractor.Parameter): String =
    strategy(param) match {
      case Some(v) => v
      case None => throw new IllegalStateException("Eventually parameter have to have some value")
    }

  private def mapDefinitionParamToEvaluatedParam(strategy: DefinitionExtractor.Parameter => Option[String])(param: DefinitionExtractor.Parameter): Parameter = {
    //TODO: enable nicer handling of constants/simple strings (maybe SpEL templates??)
    createSpelExpressionParameter(param, ensureParameterDefaultValue(strategy))
  }

  private def createSpelExpressionParameter(parameter: DefinitionExtractor.Parameter,
                                            valueEvaluatorStrategy: DefinitionExtractor.Parameter => String): Parameter =
    Parameter(parameter.name, Expression("spel", valueEvaluatorStrategy(parameter)))
}