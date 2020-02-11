package pl.touk.nussknacker.engine.api.defaults

import pl.touk.nussknacker.engine.api.definition.Parameter

trait ParameterDefaultValueDeterminer {
  def determineParameterDefaultValue(nodeDefinition: NodeDefinition, parameter: Parameter): Option[String]
}

final case class NodeDefinition(id: String, parameters: List[Parameter])
