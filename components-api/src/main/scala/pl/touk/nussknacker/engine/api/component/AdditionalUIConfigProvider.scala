package pl.touk.nussknacker.engine.api.component

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.CirceUtil.configuration
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import pl.touk.nussknacker.engine.api.parameter.{
  ParameterName,
  ParameterValueCompileTimeValidation,
  ParameterValueInput
}
import pl.touk.nussknacker.engine.graph.expression.Expression

/**
 * Trait allowing the provision of UI configuration for components and scenario properties.
 */
trait AdditionalUIConfigProvider extends Serializable {

  // Takes effect after model reload.
  def getAllForProcessingType(processingType: String): Map[DesignerWideComponentId, ComponentAdditionalConfig]

  // Takes effect immediately (doesn't require model reload).
  def getScenarioPropertiesUIConfigs(processingType: String): Map[String, ScenarioPropertyConfig]

}

object AdditionalUIConfigProvider {
  val empty = new DefaultAdditionalUIConfigProvider(Map.empty, Map.empty)
}

@JsonCodec
case class ComponentAdditionalConfig(
    parameterConfigs: Map[ParameterName, ParameterAdditionalUIConfig],
    icon: Option[String] = None,
    docsUrl: Option[String] = None,
    componentGroup: Option[ComponentGroupName] = None,
    disabled: Boolean = false
)

@JsonCodec
case class ParameterAdditionalUIConfig(
    required: Boolean,
    initialValue: Option[ParameterInitialValue],
    hintText: Option[String],
    valueEditor: Option[ParameterValueInput],
    valueCompileTimeValidation: Option[ParameterValueCompileTimeValidation]
)

@JsonCodec
sealed trait ParameterInitialValue

object ParameterInitialValue {
  case class AnyValue(expression: Expression)             extends ParameterInitialValue
  case class FixedValue(expression: FixedExpressionValue) extends ParameterInitialValue
}
