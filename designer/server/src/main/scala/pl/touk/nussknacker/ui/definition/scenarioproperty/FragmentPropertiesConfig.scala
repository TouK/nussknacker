package pl.touk.nussknacker.ui.definition.scenarioproperty

import pl.touk.nussknacker.engine.api.FragmentSpecificData
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.definition.{
  FixedExpressionValue,
  FixedExpressionValueWithIcon,
  FixedValuesParameterEditor,
  FixedValuesWithIconParameterEditor,
  MandatoryParameterValidator,
  StringParameterEditor
}
import pl.touk.nussknacker.engine.definition.component.defaultconfig.DefaultsComponentGroupName
import pl.touk.nussknacker.engine.definition.component.defaultconfig.DefaultsComponentIcon.{
  ChoiceIcon,
  CustomComponentIcon,
  EnricherIcon,
  FilterIcon,
  FragmentIcon,
  ServiceIcon,
  SinkIcon,
  SourceIcon,
  SplitIcon,
  VariableIcon
}

object FragmentPropertiesConfig {

  val docsUrlConfig: (String, ScenarioPropertyConfig) = FragmentSpecificData.docsUrlName ->
    ScenarioPropertyConfig(
      defaultValue = None,
      editor = Some(StringParameterEditor),
      // TODO: some validator
      validators = None,
      label = Some("Documentation url"),
      hintText = None
    )

  val componentGroupNameConfig: (String, ScenarioPropertyConfig) = FragmentSpecificData.componentGroupName ->
    ScenarioPropertyConfig(
      defaultValue = None,
      editor = Some(
        FixedValuesParameterEditor(
          DefaultsComponentGroupName.allAvailableForFragment.map(groupName =>
            FixedExpressionValue(groupName.value, groupName.value)
          )
        )
      ),
      validators = Some(List(MandatoryParameterValidator)),
      label = Some("Component group"),
      hintText = Some("Group of components in the Creator Panel in which this fragment will be available")
    )

  private val icons = List(
    FixedExpressionValueWithIcon(SourceIcon, "Source", SourceIcon),
    FixedExpressionValueWithIcon(SinkIcon, "Sink", SinkIcon),
    FixedExpressionValueWithIcon(EnricherIcon, "Enricher", EnricherIcon),
    FixedExpressionValueWithIcon(ServiceIcon, "Service", ServiceIcon),
    FixedExpressionValueWithIcon(CustomComponentIcon, "Custom component", CustomComponentIcon),
    FixedExpressionValueWithIcon(FragmentIcon, "Fragment", FragmentIcon),
    FixedExpressionValueWithIcon(FilterIcon, "Filter", FilterIcon),
    FixedExpressionValueWithIcon(SplitIcon, "Split", SplitIcon),
    FixedExpressionValueWithIcon(ChoiceIcon, "Choice", ChoiceIcon),
    FixedExpressionValueWithIcon(VariableIcon, "Variable", VariableIcon),
  )

  val iconConfig: (String, ScenarioPropertyConfig) = FragmentSpecificData.iconName ->
    ScenarioPropertyConfig(
      defaultValue = None,
      editor = Some(FixedValuesWithIconParameterEditor(icons)),
      validators = Some(List(MandatoryParameterValidator)),
      label = Some("Icon"),
      hintText = None
    )

  // TODO: We should probably allow to add some properties definition using configuration like in the scenario case
  val properties: Map[String, ScenarioPropertyConfig] = Map(docsUrlConfig, componentGroupNameConfig, iconConfig)

}
