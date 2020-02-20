package pl.touk.nussknacker.ui.definition.defaults

import pl.touk.nussknacker.ui.definition.UIParameter

trait ParameterDefaultValueDeterminer {
  def determineParameterDefaultValue(nodeDefinition: UINodeDefinition, parameter: UIParameter): Option[String]
}

final case class UINodeDefinition(id: String, parameters: List[UIParameter])
