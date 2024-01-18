package pl.touk.nussknacker.engine.api.component

import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import pl.touk.nussknacker.engine.api.parameter.{ParameterValueCompileTimeValidation, ValueInputWithFixedValues}

/**
 * Trait allowing the provision of UI configuration for components and scenario properties, without requiring a model reload.
 */
trait AdditionalUIConfigProvider extends Serializable {

  def getAllForProcessingType(processingType: String): Map[ComponentId, ComponentAdditionalConfig]

  // `ScenarioPropertyConfig.validators` does nothing (only usage goes to createUIScenarioPropertyConfig)
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
