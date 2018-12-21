package pl.touk.nussknacker.ui.process.uiconfig.defaults
import pl.touk.nussknacker.engine.api.definition
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValues
import pl.touk.nussknacker.engine.definition.defaults.{NodeDefinition, ParameterDefaultValueExtractorStrategy}

object RestrictionBasedDefaultValueExtractor extends ParameterDefaultValueExtractorStrategy {

  override def evaluateParameterDefaultValue(nodeDefinition: NodeDefinition, parameter: definition.Parameter): Option[String] = {
    parameter.restriction.collect { case FixedExpressionValues(firstValue::_) =>
      firstValue.expression
    }
  }
}

