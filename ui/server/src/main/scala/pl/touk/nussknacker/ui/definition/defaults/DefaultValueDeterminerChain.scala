package pl.touk.nussknacker.ui.definition.defaults

import pl.touk.nussknacker.engine.util.loader.{ModelClassLoader, ScalaServiceLoader}
import pl.touk.nussknacker.ui.definition.UIParameter

object DefaultValueDeterminerChain {
  def apply(defaultParametersValues: ParamDefaultValueConfig, modelClassLoader: ModelClassLoader): DefaultValueDeterminerChain = {
    val strategies = Seq(
      new ConfigParameterDefaultValueDeterminer(defaultParametersValues),
      OptionalityBasedDefaultValueDeterminer,
      EditorPossibleValuesBasedDefaultValueDeterminer,
      TypeRelatedParameterValueDeterminer
    )
    new DefaultValueDeterminerChain(strategies)
  }
}

class DefaultValueDeterminerChain(elements: Iterable[ParameterDefaultValueDeterminer]) extends ParameterDefaultValueDeterminer {
  override def determineParameterDefaultValue(nodeDefinition: UINodeDefinition,
                                              parameter: UIParameter): Option[String] = {
    elements.view.flatMap(_.determineParameterDefaultValue(nodeDefinition, parameter)).headOption
  }
}
