package pl.touk.nussknacker.engine.definition.component

import pl.touk.nussknacker.engine.api.component.{
  ComponentAdditionalConfig,
  ComponentConfig,
  ParameterAdditionalUIConfig,
  ParameterConfig,
  ParameterInitialValue
}
import pl.touk.nussknacker.engine.api.definition.{
  MandatoryExpressionValidator,
  ValidationExpressionParameterValidatorToCompile
}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.compile.nodecompilation.ValueEditorValidator
import pl.touk.nussknacker.engine.graph.expression.Expression

object ComponentAdditionalConfigConverter {

  def toComponentConfig(componentAdditionalConfig: ComponentAdditionalConfig): ComponentConfig =
    ComponentConfig(
      params = Some(componentAdditionalConfig.parameterConfigs.map { case (name, p) =>
        name -> toParameterConfig(p, name)
      }),
      icon = componentAdditionalConfig.icon,
      docsUrl = componentAdditionalConfig.docsUrl,
      componentGroup = componentAdditionalConfig.componentGroup,
      disabled = componentAdditionalConfig.disabled,
      componentId = None,
      label = None
    )

  private def toParameterConfig(
      paramAdditionalConfig: ParameterAdditionalUIConfig,
      paramName: ParameterName
  ): ParameterConfig = {
    val validators = (if (paramAdditionalConfig.required) List(MandatoryExpressionValidator) else List.empty) ++
      paramAdditionalConfig.valueCompileTimeValidation
        .map(validation => List(ValidationExpressionParameterValidatorToCompile(validation)))
        .getOrElse(List.empty)

    ParameterConfig(
      defaultValue = paramAdditionalConfig.initialValue.map {
        case ParameterInitialValue.AnyValue(expression)   => expression
        case ParameterInitialValue.FixedValue(expression) => Expression.spel(expression.expression)
      },
      editors = paramAdditionalConfig.valueEditor
        .flatMap(editor =>
          ValueEditorValidator
            .validateAndGetEditor(
              valueEditor = editor,
              initialValue = paramAdditionalConfig.initialValue.flatMap {
                case _: ParameterInitialValue.AnyValue            => None
                case ParameterInitialValue.FixedValue(expression) => Some(expression)
              },
              paramName = paramName,
              nodeIds = Set.empty
            )
            .toOption
        )
        .map(_.toList),
      validators = if (validators.nonEmpty) Some(validators) else None,
      label = None,
      hintText = paramAdditionalConfig.hintText,
      category = None
    )
  }

}
