package pl.touk.nussknacker.ui.definition.defaults

import pl.touk.nussknacker.restmodel.definition.UIParameter

trait ParameterDefaultValueDeterminer {
  def determineParameterDefaultValue(nodeDefinition: UINodeDefinition, parameter: UIParameter): Option[String]
}

final case class UINodeDefinition(id: String, parameters: List[UIParameter])
