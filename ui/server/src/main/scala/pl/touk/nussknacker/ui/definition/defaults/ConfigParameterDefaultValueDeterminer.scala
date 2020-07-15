package pl.touk.nussknacker.ui.definition.defaults

import pl.touk.nussknacker.engine.api.process.ParameterConfig
import pl.touk.nussknacker.restmodel.definition.UIParameter

protected class ConfigParameterDefaultValueDeterminer(config: ParamDefaultValueConfig) extends ParameterDefaultValueDeterminer {
  override def determineParameterDefaultValue(nodeDefinition: UINodeDefinition,
                                              parameter: UIParameter): Option[String] = {
    config.getNodeValue(nodeDefinition.id, parameter.name)
  }

}

case class ParamDefaultValueConfig(values: Map[String, Map[String, ParameterConfig]]) {
  def getNodeValue(node: String, value: String): Option[String] =
    values.get(node).flatMap(_.get(value).flatMap(_.defaultValue))
}

