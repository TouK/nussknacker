package pl.touk.nussknacker.engine.definition.component

import pl.touk.nussknacker.engine.api.component.{
  ComponentAdditionalConfig,
  ParameterAdditionalUIConfig,
  ParameterConfig,
  SingleComponentConfig
}
import pl.touk.nussknacker.engine.api.definition.MandatoryParameterValidator
import pl.touk.nussknacker.engine.compile.nodecompilation.ValueEditorValidator

object ComponentAdditionalConfigConverter {

  def toSingleComponentConfig(componentAdditionalConfig: ComponentAdditionalConfig): SingleComponentConfig =
    SingleComponentConfig(
      params = Some(componentAdditionalConfig.parameterConfigs.map { case (name, p) =>
        name -> toParameterConfig(p, name)
      }),
      icon = componentAdditionalConfig.icon,
      docsUrl = componentAdditionalConfig.docsUrl,
      componentGroup = componentAdditionalConfig.componentGroup,
      disabled = componentAdditionalConfig.disabled,
      componentId = None
    )

  private def toParameterConfig(
      paramAdditionalConfig: ParameterAdditionalUIConfig,
      paramName: String
  ): ParameterConfig = ParameterConfig(
    defaultValue = paramAdditionalConfig.initialValue.map(
      _.expression
    ), // validating initialValue with context and expressionCompiler will be similar to handling 'valueCompileTimeValidation', see AdditionalUIConfigProvider TODOs
    editor = paramAdditionalConfig.valueEditor.flatMap(editor =>
      ValueEditorValidator
        .validateAndGetEditor(
          valueEditor = editor,
          initialValue = paramAdditionalConfig.initialValue,
          paramName = paramName,
          nodeIds = Set.empty
        )
        .toOption
    ),
    validators = if (paramAdditionalConfig.required) {
      Some(List(MandatoryParameterValidator))
    } else {
      None
    }, // see AdditionalUIConfigProvider TODOs
    label = None,
    hintText = paramAdditionalConfig.hintText
  )

}
