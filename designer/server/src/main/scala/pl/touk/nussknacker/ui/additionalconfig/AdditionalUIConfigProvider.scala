package pl.touk.nussknacker.ui.additionalconfig

import pl.touk.nussknacker.engine.api.component.{
  ComponentGroupName,
  ComponentId,
  ParameterConfig,
  ScenarioPropertyConfig,
  SingleComponentConfig
}
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import pl.touk.nussknacker.engine.definition.component.parameter.editor.EditorExtractor
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{
  ParameterValueCompileTimeValidation,
  ValueInputWithFixedValues
}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

/**
 * Trait allowing the provision of UI configuration for components and scenario properties, without requiring a model reload.
 *
 * TODO: The current implementation allows providing configs only for standard components - meaning that base components aren't handled.
 * TODO: Currently the value of 'valueCompileTimeValidation' has no effect, it'll be supported in the future but is included now to keep the API stable.
 */
trait AdditionalUIConfigProvider extends Serializable {

  def getAllForProcessingType(processingType: String): Map[ComponentId, AdditionalUIConfig]

  def getScenarioPropertiesUIConfigs(processingType: String): Map[String, ScenarioPropertyConfig]

}

object AdditionalUIConfigProvider {
  val empty = new DefaultAdditionalUIConfigProvider(Map.empty, Map.empty)
}

case class AdditionalUIConfig(
    parameterConfigs: Map[String, ParameterAdditionalUIConfig],
    icon: Option[String] = None,
    docsUrl: Option[String] = None,
    componentGroup: Option[ComponentGroupName] = None,
    disabled: Boolean = false
) {

  def toSingleComponentConfig: SingleComponentConfig =
    SingleComponentConfig(
      params = Some(parameterConfigs.mapValuesNow(_.toParameterConfig)),
      icon = icon,
      docsUrl = docsUrl,
      componentGroup = componentGroup,
      disabled = disabled,
      componentId = None
    )

}

case class ParameterAdditionalUIConfig(
    required: Boolean = false,
    initialValue: Option[FixedExpressionValue],
    hintText: Option[String],
    valueEditor: Option[ValueInputWithFixedValues],
    valueCompileTimeValidation: Option[ParameterValueCompileTimeValidation],
) {

  def toParameterConfig: ParameterConfig = ParameterConfig(
    defaultValue = initialValue.map(_.expression),
    editor = valueEditor.map(EditorExtractor.extract),
    validators = None, // see AdditionalUIConfigProvider TODOs
    label = None,
    hintText = hintText
  )

}
