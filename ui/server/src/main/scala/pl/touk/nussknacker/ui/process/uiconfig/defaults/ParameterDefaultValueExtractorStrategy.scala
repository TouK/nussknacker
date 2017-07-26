package pl.touk.nussknacker.ui.process.uiconfig.defaults

import pl.touk.nussknacker.engine.definition.DefinitionExtractor
import pl.touk.nussknacker.ui.api.NodeDefinition

trait ParameterDefaultValueExtractorStrategy {
  def evaluateParameterDefaultValue(nodeDefinition: NodeDefinition, parameter: DefinitionExtractor.Parameter): Option[String]
}
