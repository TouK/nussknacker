package pl.touk.nussknacker.ui.process.uiconfig.defaults
import pl.touk.nussknacker.engine.definition.DefinitionExtractor
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.FixedExpressionValues
import pl.touk.nussknacker.engine.definition.defaults.{NodeDefinition, ParameterDefaultValueExtractorStrategy}

object RestrictionBasedDefaultValueExtractor extends ParameterDefaultValueExtractorStrategy {

  override def evaluateParameterDefaultValue(nodeDefinition: NodeDefinition, parameter: DefinitionExtractor.Parameter): Option[String] = {
    parameter.restriction.collect { case FixedExpressionValues(firstValue::_) =>
      firstValue.expression
    }
  }
}

