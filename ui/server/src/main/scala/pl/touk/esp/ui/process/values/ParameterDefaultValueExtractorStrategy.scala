package pl.touk.esp.ui.process.values

import pl.touk.esp.engine.definition.DefinitionExtractor
import pl.touk.esp.ui.api.NodeDefinition

trait ParameterDefaultValueExtractorStrategy {
  def evaluateParameterDefaultValue(nodeDefinition: NodeDefinition, parameter: DefinitionExtractor.Parameter): Option[String]
}
