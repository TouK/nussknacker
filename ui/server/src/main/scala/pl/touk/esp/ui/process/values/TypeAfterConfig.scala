package pl.touk.esp.ui.process.values

import pl.touk.esp.engine.definition.DefinitionExtractor
import pl.touk.esp.ui.api.NodeDefinition

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



