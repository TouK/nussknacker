package pl.touk.nussknacker.engine.compile.nodecompilation

import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.graph.evaluatedparam
import pl.touk.nussknacker.engine.graph.expression.Expression

object InitialParametersGenericNodeEnricher {

  def enrichWithInitialParameters(parametersFromNode: List[evaluatedparam.Parameter], parametersFromDynamicDefinition: List[Parameter]): List[evaluatedparam.Parameter] = {
    val parametersNamesFromNode = parametersFromNode.map(_.name).toSet
    val enrichedParametersAfterFirstValidation = parametersFromDynamicDefinition
      .filterNot(p => parametersNamesFromNode.contains(p.name))
      .map(p => evaluatedparam.Parameter(p.name, Expression("spel", p.defaultValue.getOrElse(""))))
    parametersFromNode ++ enrichedParametersAfterFirstValidation
  }

}
