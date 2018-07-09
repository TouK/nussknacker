package pl.touk.nussknacker.ui.process.uiconfig.defaults

import pl.touk.nussknacker.engine.definition.DefinitionExtractor
import pl.touk.nussknacker.ui.api.NodeDefinition

object DefaultValueExtractorChain {

  def apply(defaultParametersValues: ParamDefaultValueConfig): ParameterDefaultValueExtractorStrategy = {
    new DefaultValueExtractorChain(Seq(new ConfigParameterDefaultValueExtractor(defaultParametersValues),
      RestrictionBasedDefaultValueExtractor,
      TypeRelatedParameterValueExtractor))
  }

}

class DefaultValueExtractorChain(elements: Iterable[ParameterDefaultValueExtractorStrategy]) extends ParameterDefaultValueExtractorStrategy {
  override def evaluateParameterDefaultValue(nodeDefinition: NodeDefinition,
                                             parameter: DefinitionExtractor.Parameter): Option[String] = {
    elements.view.flatMap(_.evaluateParameterDefaultValue(nodeDefinition, parameter)).headOption
  }
}



