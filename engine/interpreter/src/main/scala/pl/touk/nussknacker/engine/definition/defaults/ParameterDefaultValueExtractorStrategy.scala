package pl.touk.nussknacker.engine.definition.defaults

import pl.touk.nussknacker.engine.definition.DefinitionExtractor

trait ParameterDefaultValueExtractorStrategy {
  def evaluateParameterDefaultValue(nodeDefinition: NodeDefinition, parameter: DefinitionExtractor.Parameter): Option[String]
}

final case class NodeDefinition(id: String, parameters: List[DefinitionExtractor.Parameter])
