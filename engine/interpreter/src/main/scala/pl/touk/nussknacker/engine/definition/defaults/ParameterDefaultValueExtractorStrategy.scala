package pl.touk.nussknacker.engine.definition.defaults

import pl.touk.nussknacker.engine.api.definition.Parameter

trait ParameterDefaultValueExtractorStrategy {
  def evaluateParameterDefaultValue(nodeDefinition: NodeDefinition, parameter: Parameter): Option[String]
}

final case class NodeDefinition(id: String, parameters: List[Parameter])
