package pl.touk.nussknacker.ui.additionalconfig

import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.compile.nodecompilation.ValueEditorValidator
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{
  FixedExpressionValue,
  ParameterValueCompileTimeValidation,
  ValueInputWithFixedValues
}

/**
 * Trait allowing the provision of UI configuration for components and scenario properties, without requiring a model reload.
 *
 * TODO: The current implementation allows providing configs only for standard components - meaning that base components aren't handled.
 * TODO: Currently the value of 'valueCompileTimeValidation' has no effect, it'll be supported in the future but is included now to keep the API stable.
 *       Validating and extracting a parameter's validators requires access to expressionCompiler and validationContext,
 *       this is currently hard to achieve where AdditionalUIConfigProvider is used (UIProcessObjectsFactory),
 *       after refactoring it to be used at the level of ModelData or so it should be easy, and support for 'valueCompileTimeValidation' will be possible
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
      params = Some(parameterConfigs.map { case (name, p) => name -> p.toParameterConfig(name) }),
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
    valueCompileTimeValidation: Option[
      ParameterValueCompileTimeValidation
    ], // not supported yet, see AdditionalUIConfigProvider TODOs
) {

  def toParameterConfig(paramName: String): ParameterConfig = ParameterConfig(
    defaultValue = initialValue.map(
      _.expression
    ), // TODO validating initialValue with context and expressionCompiler will be similar to handling 'valueCompileTimeValidation'
    editor = valueEditor.flatMap(editor =>
      ValueEditorValidator
        .validateAndGetEditor(
          valueEditor = editor,
          initialValue = initialValue,
          paramName = paramName,
          nodeIds = Set.empty
        )
        .toOption
    ),
    validators = None, // see AdditionalUIConfigProvider TODOs
    label = None,
    hintText = hintText
  )

}
