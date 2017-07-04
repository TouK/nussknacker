package pl.touk.esp.ui.process.values

import pl.touk.esp.engine.definition.DefinitionExtractor
import pl.touk.esp.ui.api.NodeDefinition

class ConfigParameterDefaultValueExtractor(config: ParamDefaultValueConfig) extends ParameterDefaultValueExtractorStrategy {
  override def evaluateParameterDefaultValue(nodeDefinition: NodeDefinition,
                                             parameter: DefinitionExtractor.Parameter): Option[String] = {
    config.getNodeValue(nodeDefinition.id, parameter.name)
  }

}

class ParamDefaultValueConfig(values: Map[String, Map[String, String]]) {
  def getNodeValue(node: String, value: String): Option[String] =
    values.get(node).flatMap(_.get(value))
}

