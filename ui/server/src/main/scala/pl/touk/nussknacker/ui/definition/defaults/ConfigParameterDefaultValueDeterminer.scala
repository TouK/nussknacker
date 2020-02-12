package pl.touk.nussknacker.ui.definition.defaults

import pl.touk.nussknacker.engine.api.defaults.{NodeDefinition, ParameterDefaultValueDeterminer}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ParameterConfig

class ConfigParameterDefaultValueDeterminer(config: ParamDefaultValueConfig) extends ParameterDefaultValueDeterminer {
  override def determineParameterDefaultValue(nodeDefinition: NodeDefinition,
                                              parameter: Parameter): Option[String] = {
    config.getNodeValue(nodeDefinition.id, parameter.name)
  }

}

case class ParamDefaultValueConfig(values: Map[String, Map[String, ParameterConfig]]) {
  def getNodeValue(node: String, value: String): Option[String] =
    values.get(node).flatMap(_.get(value).flatMap(_.defaultValue))
}

