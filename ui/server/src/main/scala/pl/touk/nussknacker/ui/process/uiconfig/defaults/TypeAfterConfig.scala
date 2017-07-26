package pl.touk.nussknacker.ui.process.uiconfig.defaults

import pl.touk.nussknacker.engine.definition.DefinitionExtractor
import pl.touk.nussknacker.ui.api.NodeDefinition

class TypeAfterConfig(config: ParamDefaultValueConfig) extends ParameterDefaultValueExtractorStrategy {
  override def evaluateParameterDefaultValue(nodeDefinition: NodeDefinition,
                                             parameter: DefinitionExtractor.Parameter): Option[String] = {
    val configExtractor = new ConfigParameterDefaultValueExtractor(config)
    List(configExtractor, TypeRelatedParameterValueExtractor).map {
      _.evaluateParameterDefaultValue(nodeDefinition, parameter)
    } filter {
      _.isDefined
    } head
  }
}



