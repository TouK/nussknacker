package pl.touk.nussknacker.engine.definition.component

import pl.touk.nussknacker.engine.api.component.{
  ComponentAdditionalConfig,
  ParameterAdditionalUIConfig,
  ParameterConfig,
  SingleComponentConfig
}
import pl.touk.nussknacker.engine.api.definition.{
  MandatoryParameterValidator,
  ValidationExpressionParameterValidatorToCompile
}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
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
      paramName: ParameterName
  ): ParameterConfig = {
    val validators = (if (paramAdditionalConfig.required) List(MandatoryParameterValidator) else List.empty) ++
      paramAdditionalConfig.valueCompileTimeValidation
        .map(validation => List(ValidationExpressionParameterValidatorToCompile(validation)))
        .getOrElse(List.empty)

    ParameterConfig(
      defaultValue = paramAdditionalConfig.initialValue.map(
        _.expression
      ), // TODO currently this isn't validated (e.g. can be of incorrect type) - not a big issue as it's only used to initially fill the FE form, if sent with this wrong value the process will fail validation
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
      validators = if (validators.nonEmpty) Some(validators) else None,
      label = None,
      hintText = paramAdditionalConfig.hintText
    )
  }

}
