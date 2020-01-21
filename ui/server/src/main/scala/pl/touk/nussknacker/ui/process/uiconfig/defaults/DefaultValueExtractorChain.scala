package pl.touk.nussknacker.ui.process.uiconfig.defaults

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.definition.defaults.{NodeDefinition, ParameterDefaultValueExtractorStrategy}
import pl.touk.nussknacker.engine.util.loader.{ModelClassLoader, ScalaServiceLoader}

object DefaultValueExtractorChain extends LazyLogging {
  def apply(defaultParametersValues: ParamDefaultValueConfig, modelClassLoader: ModelClassLoader): DefaultValueExtractorChain = {
    val userStrategies = ScalaServiceLoader
      .load[ParameterDefaultValueExtractorStrategy](modelClassLoader.classLoader)
    val nkStrategies = Seq(
      new ConfigParameterDefaultValueExtractor(defaultParametersValues),
      EditorPossibleValuesBasedDefaultValueExtractor,
      TypeRelatedParameterValueExtractor
    )

    val allStrategies = userStrategies ++ nkStrategies
    logger.debug("Building DefaultValueExtractorChain with strategies: {}", allStrategies)
    new DefaultValueExtractorChain(allStrategies)
  }
}

class DefaultValueExtractorChain(elements: Iterable[ParameterDefaultValueExtractorStrategy]) extends ParameterDefaultValueExtractorStrategy {
  override def evaluateParameterDefaultValue(nodeDefinition: NodeDefinition,
                                             parameter: Parameter): Option[String] = {
    elements.view.flatMap(_.evaluateParameterDefaultValue(nodeDefinition, parameter)).headOption
  }
}