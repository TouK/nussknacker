package pl.touk.nussknacker.ui.definition.defaults

import pl.touk.nussknacker.engine.api.defaults.{NodeDefinition, ParameterDefaultValueDeterminer}
import pl.touk.nussknacker.engine.api.definition.Parameter

object OptionalityBasedDefaultValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(nodeDefinition: NodeDefinition, parameter: Parameter): Option[String] =
    if (parameter.isOptional) Some("") else None

}
