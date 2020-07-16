package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.restmodel.definition.UIParameter
import pl.touk.nussknacker.ui.definition.defaults.{ParameterDefaultValueDeterminer, UINodeDefinition}

class EvaluatedParameterPreparer(defaultValueEvaluator: ParameterDefaultValueDeterminer) {
  def prepareEvaluatedParameter(nodeDefinition: UINodeDefinition): List[Parameter] = {
    val strategy: UIParameter => Option[String] = p => defaultValueEvaluator.determineParameterDefaultValue(nodeDefinition, p)
    nodeDefinition.parameters
      .filterNot(_.branchParam)
      .map(mapDefinitionParamToEvaluatedParam(strategy))
  }

  def prepareEvaluatedBranchParameter(nodeDefinition: UINodeDefinition): List[Parameter] = {
    val strategy: UIParameter => Option[String] = p => defaultValueEvaluator.determineParameterDefaultValue(nodeDefinition, p)
    nodeDefinition.parameters
      .filter(_.branchParam)
      .map(mapDefinitionParamToEvaluatedParam(strategy))
  }

  private def determineDefaultValue(strategy: UIParameter => Option[String])(param: UIParameter): String =
    strategy(param) match {
      case Some(v) => v
      case None => "" // parameter have to have some default value
    }

  private def mapDefinitionParamToEvaluatedParam(strategy: UIParameter => Option[String])(param: UIParameter): Parameter = {
    //TODO: enable nicer handling of constants/simple strings (maybe SpEL templates??)
    createSpelExpressionParameter(param, determineDefaultValue(strategy))
  }

  private def createSpelExpressionParameter(parameter: UIParameter,
                                            valueEvaluatorStrategy: UIParameter => String): Parameter =
    Parameter(parameter.name, Expression("spel", valueEvaluatorStrategy(parameter)))
}
