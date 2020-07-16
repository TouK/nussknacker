package pl.touk.nussknacker.ui.definition.defaults

import pl.touk.nussknacker.restmodel.definition.UIParameter

protected object OptionalityBasedDefaultValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(nodeDefinition: UINodeDefinition, parameter: UIParameter): Option[String] =
    if (parameter.isOptional) Some("") else None

}
