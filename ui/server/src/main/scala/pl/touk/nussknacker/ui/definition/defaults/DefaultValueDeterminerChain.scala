package pl.touk.nussknacker.ui.definition.defaults

import pl.touk.nussknacker.engine.api.defaults._
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.util.loader.{ModelClassLoader, ScalaServiceLoader}

object DefaultValueDeterminerChain {
  def apply(defaultParametersValues: ParamDefaultValueConfig, modelClassLoader: ModelClassLoader): DefaultValueDeterminerChain = {
    val userStrategies = ScalaServiceLoader
      .load[ParameterDefaultValueDeterminer](modelClassLoader.classLoader)
    val nkStrategies = Seq(
      new ConfigParameterDefaultValueDeterminer(defaultParametersValues),
      OptionalityBasedDefaultValueDeterminer,
      EditorPossibleValuesBasedDefaultValueDeterminer,
      TypeRelatedParameterValueDeterminer
    )

    val allStrategies = userStrategies ++ nkStrategies
    new DefaultValueDeterminerChain(allStrategies)
  }
}

class DefaultValueDeterminerChain(elements: Iterable[ParameterDefaultValueDeterminer]) extends ParameterDefaultValueDeterminer {
  override def determineParameterDefaultValue(nodeDefinition: NodeDefinition,
                                              parameter: Parameter): Option[String] = {
    elements.view.flatMap(_.determineParameterDefaultValue(nodeDefinition, parameter)).headOption
  }
}
