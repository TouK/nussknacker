package pl.touk.nussknacker.engine.api.component

import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import pl.touk.nussknacker.engine.api.parameter.{ParameterValueCompileTimeValidation, ValueInputWithFixedValues}

/**
 * Trait allowing the provision of UI configuration for components and scenario properties.
 */
trait AdditionalUIConfigProvider extends Serializable {

  // Takes effect after model reload.
  def getAllForProcessingType(processingType: String): Map[DesignerWideComponentId, ComponentAdditionalConfig]

  // `ScenarioPropertyConfig.validators` currently does nothing (only usage goes to createUIScenarioPropertyConfig)
  // Takes effect immediately (doesn't require model reload).
  def getScenarioPropertiesUIConfigs(processingType: String): Map[String, ScenarioPropertyConfig]

}

object AdditionalUIConfigProvider {
  val empty = new DefaultAdditionalUIConfigProvider(Map.empty, Map.empty)
}

case class ComponentAdditionalConfig(
    parameterConfigs: Map[String, ParameterAdditionalUIConfig],
    icon: Option[String] = None,
    docsUrl: Option[String] = None,
    componentGroup: Option[ComponentGroupName] = None,
    disabled: Boolean = false
)

case class ParameterAdditionalUIConfig(
    required: Boolean,
    initialValue: Option[FixedExpressionValue],
    hintText: Option[String],
    valueEditor: Option[ValueInputWithFixedValues],
    valueCompileTimeValidation: Option[ParameterValueCompileTimeValidation]
)
