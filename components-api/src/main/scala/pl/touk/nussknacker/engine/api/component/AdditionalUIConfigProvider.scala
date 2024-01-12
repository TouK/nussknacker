package pl.touk.nussknacker.engine.api.component

import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import pl.touk.nussknacker.engine.api.parameter.{ParameterValueCompileTimeValidation, ValueInputWithFixedValues}

/**
 * Trait allowing the provision of UI configuration for components and scenario properties, without requiring a model reload.
 *
 * TODO: Currently the value of 'valueCompileTimeValidation' has no effect, it'll be supported in the future but is included now to keep the API stable.
 *       Validating and extracting a parameter's validators requires access to expressionCompiler and validationContext,
 *       this is currently hard to achieve where AdditionalUIConfigProvider is used (DefinitionsService and ComponentService),
 *       after refactoring it to be used at the level of ModelData or so it should be easy, and support for 'valueCompileTimeValidation' will be possible
 */
trait AdditionalUIConfigProvider extends Serializable {

  def getAllForProcessingType(processingType: String): Map[ComponentId, ComponentAdditionalConfig]

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
    required: Boolean = false,
    initialValue: Option[FixedExpressionValue],
    hintText: Option[String],
    valueEditor: Option[ValueInputWithFixedValues],
    valueCompileTimeValidation: Option[
      ParameterValueCompileTimeValidation
    ], // not supported yet, see AdditionalUIConfigProvider TODOs
)
